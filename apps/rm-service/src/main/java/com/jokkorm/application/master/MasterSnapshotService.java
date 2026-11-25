package com.jokkorm.application.master;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.master.MasterArchiveView;
import com.jokkorm.domain.master.MasterRecordSnapshot;
import com.jokkorm.domain.master.MasterRecordSnapshotRepository;
import com.jokkorm.domain.master.MasterUnreachableException;
import com.jokkorm.domain.master.SaeMasterPort;
import com.jokkorm.domain.record.Cote;
import com.jokkorm.domain.record.PhysicalArchiveRecord;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Copie locale en lecture seule de ce que le maître sait d'un dossier pour le ranger
 * (étude du 24 novembre 2026, D3 ; pattern 03).
 *
 * <p>Le maître reste le seul propriétaire : la copie n'est écrite qu'à partir de sa réponse, jamais
 * à la main. Elle est prise à l'import, refaite quand le veilleur voit l'archive changer, et relue
 * périodiquement ({@link #refreshStale}) pour ce que le veilleur ne voit pas (règle de
 * conservation modifiée chez le maître…).
 */
@Service
public class MasterSnapshotService {

    private static final Logger LOG = LoggerFactory.getLogger(MasterSnapshotService.class);

    /** Ce que JokkoRM sait d'un dossier pour le ranger, ou pourquoi il ne le sait pas. */
    public sealed interface Facts {
        /** Le maître a répondu (maintenant ou lors d'une relecture). */
        record Known(MasterRecordSnapshot snapshot) implements Facts {}
        /** Le maître ne connaît pas ce dossier. */
        record NotFound() implements Facts {}
        /** Le maître n'a pas répondu et aucune copie n'existe. */
        record Unreachable(String reason) implements Facts {}
    }

    private final SaeMasterPort master;
    private final MasterRecordSnapshotRepository snapshots;
    private final Clock clock;
    private final Duration maxAge;

    public MasterSnapshotService(SaeMasterPort master, MasterRecordSnapshotRepository snapshots, Clock clock,
                                 @Value("${jokkorm.master.snapshot.max-age:P1D}") Duration maxAge) {
        this.master = master;
        this.snapshots = snapshots;
        this.clock = clock;
        this.maxAge = maxAge;
    }

    /** Enregistre la réponse que l'appelant vient de lire chez le maître (import). */
    @Transactional
    public MasterRecordSnapshot record(TenantId tenant, UUID recordId, MasterArchiveView view) {
        MasterRecordSnapshot s = MasterRecordSnapshot.of(tenant, recordId, master.masterKind(tenant), view, clock.instant());
        snapshots.save(s);
        return s;
    }

    /**
     * Relit le dossier chez le maître et remplace la copie.
     *
     * @throws MasterUnreachableException maître injoignable (la copie existante est gardée)
     */
    @Transactional
    public Optional<MasterRecordSnapshot> refresh(TenantId tenant, UUID recordId, Cote cote) {
        Optional<MasterArchiveView> view = master.fetchByCote(tenant, cote);
        return view.map(v -> record(tenant, recordId, v));
    }

    /**
     * Relit le dossier chez le maître avant de refuser une mise en boîte sur la foi de la copie : une
     * fiche corrigée à l'instant dans le maître compte tout de suite.
     *
     * <p>Volontairement sans {@code @Transactional} : un maître injoignable ne doit pas condamner la
     * transaction de l'appelant (qui peut encore aboutir). La copie relue s'écrit dans cette
     * transaction ; si la mise en boîte est refusée, elle est annulée avec le reste, sans dommage.
     *
     * @throws MasterUnreachableException maître injoignable : l'appelant arrête de relire
     */
    public Optional<MasterRecordSnapshot> rereadNow(TenantId tenant, UUID recordId, Cote cote) {
        return master.fetchByCote(tenant, cote).map(v -> record(tenant, recordId, v));
    }

    /**
     * Données de rangement des dossiers : la copie si elle existe, sinon une lecture chez le maître
     * (enregistrée). Le maître injoignable sans copie donne {@link Facts.Unreachable} : l'appelant
     * décide (les règles de mise en boîte refusent).
     */
    @Transactional
    public Map<UUID, Facts> factsFor(TenantId tenant, Collection<PhysicalArchiveRecord> records) {
        Map<UUID, Facts> out = new HashMap<>();
        Map<UUID, MasterRecordSnapshot> known =
            snapshots.findAll(tenant, records.stream().map(PhysicalArchiveRecord::id).toList());
        // Au premier maître muet, les dossiers suivants sans copie ne le rappellent pas : sur une liaison
        // dégradée, chaque appel coûterait le délai d'attente entier, sous le verrou de la boîte.
        String down = null;
        for (PhysicalArchiveRecord r : records) {
            MasterRecordSnapshot s = known.get(r.id());
            if (s != null) {
                out.put(r.id(), new Facts.Known(s));
                continue;
            }
            if (down != null) {
                out.put(r.id(), new Facts.Unreachable(down));
                continue;
            }
            try {
                out.put(r.id(), refresh(tenant, r.id(), r.cote())
                    .<Facts>map(Facts.Known::new).orElseGet(Facts.NotFound::new));
            } catch (MasterUnreachableException e) {
                down = e.getMessage();
                out.put(r.id(), new Facts.Unreachable(down));
            }
        }
        return out;
    }

    /** Vrai si l'espace n'a pas de maître : ni service ni sort final connus, règles de regroupement inactives. */
    public boolean standalone(TenantId tenant) {
        return master.isStandalone(tenant);
    }

    /** Copies existantes, sans appeler le maître (affichage de listes). */
    @Transactional(readOnly = true)
    public Map<UUID, MasterRecordSnapshot> known(TenantId tenant, Collection<UUID> recordIds) {
        return snapshots.findAll(tenant, recordIds);
    }

    /** Relecture périodique de tous les espaces qui ont un maître. */
    public void refreshAllStale(int limitPerTenant) {
        for (TenantId tenant : snapshots.tenants()) {
            try {
                int n = refreshStale(tenant, limitPerTenant);
                if (n > 0) LOG.info("Copies maître relues (tenant {}) : {}", tenant.value(), n);
            } catch (RuntimeException e) {
                LOG.error("Relecture des copies maître (tenant {}) interrompue", tenant.value(), e);
            }
        }
    }

    /**
     * Relecture périodique : dossiers sans copie, ou dont la copie a plus de {@code max-age}.
     * S'arrête au premier maître injoignable (on réessaiera au prochain passage).
     *
     * @return nombre de copies refaites
     */
    public int refreshStale(TenantId tenant, int limit) {
        if (master.isStandalone(tenant)) return 0;
        List<MasterRecordSnapshotRepository.Stale> stale =
            snapshots.findStale(tenant, clock.instant().minus(maxAge), limit);
        int done = 0;
        List<String> missing = new ArrayList<>();
        for (var s : stale) {
            try {
                if (refresh(tenant, s.recordId(), s.cote()).isPresent()) done++;
                else missing.add(s.cote().value());
            } catch (MasterUnreachableException e) {
                LOG.warn("Relecture des copies maître interrompue (tenant {}) : {}", tenant.value(), e.getMessage());
                break;
            }
        }
        if (!missing.isEmpty()) {
            LOG.warn("Relecture des copies maître (tenant {}) : {} cote(s) inconnue(s) du maître : {}",
                tenant.value(), missing.size(), missing.size() > 5 ? missing.subList(0, 5) + "…" : missing);
        }
        return done;
    }
}
