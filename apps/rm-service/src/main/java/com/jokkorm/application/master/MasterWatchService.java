package com.jokkorm.application.master;

import com.jokkorm.application.audit.AuditChainService;
import com.jokkorm.application.record.ApplyLegalHoldUseCase;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.audit.AuditAction;
import com.jokkorm.domain.audit.AuditTargetType;
import com.jokkorm.domain.loan.PhysicalLoan;
import com.jokkorm.domain.loan.PhysicalLoanRepository;
import com.jokkorm.domain.master.MasterAlert;
import com.jokkorm.domain.master.MasterAlertRepository;
import com.jokkorm.domain.master.MasterArchiveRef;
import com.jokkorm.domain.master.MasterEvent;
import com.jokkorm.domain.master.MasterSync;
import com.jokkorm.domain.master.MasterSyncRepository;
import com.jokkorm.domain.master.MasterUnreachableException;
import com.jokkorm.domain.master.MasterWatchState;
import com.jokkorm.domain.master.MasterWatchStateRepository;
import com.jokkorm.domain.master.SaeMasterPort;
import com.jokkorm.domain.record.Cote;
import com.jokkorm.domain.record.PhysicalArchiveRecord;
import com.jokkorm.domain.record.PhysicalArchiveRecordRepository;
import com.jokkorm.domain.record.PhysicalArchiveStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Veilleur du journal du maître (étape 1, pattern 03).
 *
 * <p>Toutes les minutes, pour chaque tenant dont le maître publie un journal :
 * <ul>
 *   <li><b>métadonnées modifiées</b> par quelqu'un d'autre que JokkoRM : si l'emplacement affiché
 *       n'est plus celui que JokkoRM a envoyé, il est rétabli et l'écart est signalé. Un texte tapé
 *       dans le maître n'est pas un geste (pas de scan, pas d'agent nommé) : il ne remonte jamais ;</li>
 *   <li><b>gel</b> : l'archive passe à « gelée » dans JokkoRM, avec une alerte si sa boîte est en prêt
 *       ou demandée ; <b>levée du gel</b> : retour à la normale ;</li>
 *   <li><b>dépôt</b> d'une nouvelle archive : elle rejoint la liste « à importer », ou entre seule si
 *       son profil a la règle d'import automatique (étape 2, {@link MasterImportService}).</li>
 * </ul>
 *
 * <p>Fiabilité : le curseur est l'horodatage du MAÎTRE du dernier événement traité (jamais l'horloge
 * de JokkoRM) ; chaque lecture repart deux minutes avant. Le signalement, unique par événement et
 * par type, est le marqueur « déjà traité » : relire ne rejoue ni une action ni une ligne d'audit.
 * Maître injoignable : le curseur ne bouge pas, rien n'est perdu.
 *
 * <p>Données personnelles : seuls le type, l'archive, l'auteur et la date de l'événement sont lus.
 */
@Service
public class MasterWatchService {

    private static final Logger LOG = LoggerFactory.getLogger(MasterWatchService.class);
    /** Auteur des actions du veilleur dans l'audit de JokkoRM. */
    public static final String ACTOR = "veilleur";
    static final Duration OVERLAP = Duration.ofMinutes(2);
    static final Set<MasterEvent.Kind> KINDS = Collections.unmodifiableSet(EnumSet.allOf(MasterEvent.Kind.class));
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public record Run(boolean ok, int events, int raised, String error) {
    }

    public record Status(boolean available, Instant lastRunAt, Instant lastSuccessAt, String lastError,
                         Instant cursorAt, int lastEvents, int openActions) {
    }

    private final SaeMasterPort master;
    private final MasterWatchStateRepository states;
    private final MasterAlertRepository alerts;
    private final MasterSyncRepository queue;
    private final MasterSyncService sync;
    private final PhysicalArchiveRecordRepository records;
    private final PhysicalLoanRepository loans;
    private final ApplyLegalHoldUseCase legalHold;
    private final AuditChainService audit;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Duration initialLookback;
    private final MasterImportService imports;
    private final MasterSnapshotService snapshots;
    /** Événements déjà traités récemment : évite de relire le maître pour la fenêtre de recouvrement. */
    private final Map<String, Boolean> recentlyHandled = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > 5000;
        }
    });

    public MasterWatchService(SaeMasterPort master, MasterWatchStateRepository states, MasterAlertRepository alerts,
                              MasterSyncRepository queue, MasterSyncService sync, PhysicalArchiveRecordRepository records,
                              PhysicalLoanRepository loans, ApplyLegalHoldUseCase legalHold, AuditChainService audit,
                              TransactionTemplate tx, Clock clock,
                              @Value("${jokkorm.master.watch.initial-lookback-hours:24}") long initialLookbackHours,
                              MasterImportService imports, MasterSnapshotService snapshots) {
        this.imports = imports;
        this.snapshots = snapshots;
        this.master = master;
        this.states = states;
        this.alerts = alerts;
        this.queue = queue;
        this.sync = sync;
        this.records = records;
        this.loans = loans;
        this.legalHold = legalHold;
        this.audit = audit;
        this.tx = tx;
        this.clock = clock;
        this.initialLookback = Duration.ofHours(Math.max(0, initialLookbackHours));
    }

    // ─── Passage du veilleur ─────────────────────────────────────────────────────

    /** Un passage pour chaque tenant dont le maître publie un journal. */
    public void watchAll() {
        List<TenantId> tenants = tx.execute(s -> states.tenants());
        if (tenants == null) return;
        for (TenantId tenant : tenants) {
            if (!master.publishesEvents(tenant)) continue;
            try {
                watch(tenant);
            } catch (RuntimeException e) {
                LOG.error("[veilleur] {} : passage interrompu", tenant.value(), e);
            }
        }
    }

    public Run watch(TenantId tenant) {
        Instant now = clock.instant();
        MasterWatchState state = tx.execute(s -> states.find(tenant))
            .orElseGet(() -> MasterWatchState.start(tenant, now.minus(initialLookback)));
        Instant from = state.cursorAt().minus(OVERLAP);
        List<MasterEvent> events;
        try {
            events = TenantContext.callWithUnchecked(tenant, () -> master.readEvents(tenant, from, KINDS));
        } catch (MasterUnreachableException e) {
            return fail(state, state.cursorAt(), now, e);
        }
        // Le maître ne dit pas quel champ a changé et describe() lit l'état ACTUEL : pour une même
        // archive, seul le dernier événement « métadonnées » du passage est évalué. Sinon une écriture
        // de JokkoRM suivie d'une modification à la main ferait un faux « à vérifier », et deux
        // modifications dans la minute un double rétablissement.
        Map<String, MasterEvent> latestMetadata = new java.util.HashMap<>();
        for (MasterEvent ev : events) {
            if (ev.kind() == MasterEvent.Kind.METADATA_MODIFIED && !recentlyHandled.containsKey(tenant.value() + "|" + ev.id())) {
                latestMetadata.put(ev.masterArchiveId(), ev);  // événements du plus ancien au plus récent
            }
        }
        Instant cursor = state.cursorAt();
        int raised = 0;
        for (MasterEvent ev : events) {
            String key = tenant.value() + "|" + ev.id();
            boolean superseded = ev.kind() == MasterEvent.Kind.METADATA_MODIFIED
                && latestMetadata.get(ev.masterArchiveId()) != ev;
            if (!recentlyHandled.containsKey(key) && !superseded) {
                try {
                    if (TenantContext.callWithUnchecked(tenant, () -> handle(tenant, ev))) raised++;
                } catch (MasterUnreachableException | DataAccessException | TransactionException e) {
                    // Panne : on s'arrête AVANT cet événement, il sera relu au prochain passage.
                    return fail(state, cursor, now, e);
                } catch (RuntimeException e) {
                    // Cas métier (archive détruite, cote illisible…) : journalisé et passé, pour ne pas bloquer le veilleur.
                    LOG.warn("[veilleur] {} : événement {} ({}) ignoré : {}", tenant.value(), ev.id(), ev.kind(), e.getMessage());
                }
            }
            recentlyHandled.put(key, Boolean.TRUE);
            if (ev.occurredAt().isAfter(cursor)) cursor = ev.occurredAt();
        }
        MasterWatchState done = state.succeeded(cursor, now, events.size());
        tx.executeWithoutResult(s -> states.save(done));
        if (raised > 0) LOG.info("[veilleur] {} : {} événement(s) lu(s), {} signalement(s)", tenant.value(), events.size(), raised);
        return new Run(true, events.size(), raised, null);
    }

    private Run fail(MasterWatchState state, Instant cursor, Instant now, RuntimeException e) {
        LOG.warn("[veilleur] {} : {}", state.tenant().value(), e.getMessage());
        MasterWatchState failed = state.failed(cursor, now, e.getMessage());
        tx.executeWithoutResult(s -> states.save(failed));
        return new Run(false, 0, 0, e.getMessage());
    }

    /** Vrai si un signalement a été créé. Les appels au maître se font hors transaction. */
    boolean handle(TenantId tenant, MasterEvent ev) {
        if (ev.kind() == MasterEvent.Kind.METADATA_MODIFIED && !master.acceptsPhysicalStatus(tenant)) return false;
        MasterArchiveRef ref = master.describe(tenant, ev.masterArchiveId()).orElse(null);
        if (ev.kind() == MasterEvent.Kind.DEPOSITED) {
            // Étape 2 : même illisible, un dépôt laisse une trace (jamais perdu en silence).
            MasterImportService.Outcome o = tx.execute(s -> imports.onDeposited(tenant, ev, ref));
            return o == MasterImportService.Outcome.PENDING || o == MasterImportService.Outcome.AUTO_IMPORTED
                || o == MasterImportService.Outcome.UNREADABLE;
        }
        if (ref == null) return false;  // archive inconnue ou invisible pour le compte de connexion
        Cote cote = Cote.of(ref.cote());
        // D3 : l'archive a changé chez le maître → sa copie de rangement est relue (hors transaction ;
        // maître injoignable = panne, l'événement sera relu au prochain passage).
        records.findByCote(tenant, cote).ifPresent(r -> snapshots.refresh(tenant, r.id(), cote));
        Boolean raised = tx.execute(s -> switch (ev.kind()) {
            case METADATA_MODIFIED -> onMetadataModified(tenant, ev, cote, ref.displayedPhysicalStatus());
            case FROZEN -> onFrozen(tenant, ev, cote);
            case UNFROZEN -> onUnfrozen(tenant, ev, cote);
            case DEPOSITED -> false;  // traité plus haut
        });
        return Boolean.TRUE.equals(raised);
    }

    private boolean onMetadataModified(TenantId tenant, MasterEvent ev, Cote cote, String displayed) {
        PhysicalArchiveRecord record = records.findByCote(tenant, cote).orElse(null);
        if (record == null) return false;
        MasterSync last = queue.find(tenant, cote.value()).orElse(null);
        if (same(displayed, last == null ? null : last.lastPayload())) return false;
        Instant now = clock.instant();
        if (ev.byConnector()) {
            // Écriture de JokkoRM lui-même : un envoi plus récent est en route, ou le maître a modifié la
            // valeur en l'enregistrant. Jamais de réécriture ici (elle tournerait en boucle).
            if (last != null && last.status() == MasterSync.Status.PENDING) return false;
            return alerts.insertIfAbsent(MasterAlert.raise(tenant, MasterAlert.Kind.LOCATION_MISMATCH, cote.value(),
                record.id(), ev, "Juste après l'écriture de JokkoRM, une autre valeur est affichée : à vérifier.", now));
        }
        boolean erased = displayed == null || displayed.isBlank();
        String detail = (erased ? "Emplacement effacé" : "Emplacement modifié à la main")
            + " : JokkoRM l'a rétabli (seul un geste physique change un emplacement).";
        if (!alerts.insertIfAbsent(MasterAlert.raise(tenant, MasterAlert.Kind.LOCATION_RESTORED, cote.value(),
            record.id(), ev, detail, now))) {
            return false;
        }
        sync.touchRecord(tenant, cote);
        audit.record(AuditAction.MASTER_LOCATION_RESTORED, AuditTargetType.PHYSICAL_ARCHIVE_RECORD, record.id(), ACTOR,
            "cote=" + cote.value() + "; evenement-maitre=" + ev.id() + "; par=" + ev.actor()
                + "; constat=" + (erased ? "efface" : "modifie"));
        return true;
    }

    private boolean onFrozen(TenantId tenant, MasterEvent ev, Cote cote) {
        PhysicalArchiveRecord record = records.findByCote(tenant, cote).orElse(null);
        if (record == null) return false;
        PhysicalLoan loan = record.containerId().flatMap(c -> loans.findActiveByContainer(tenant, c)).orElse(null);
        MasterAlert.Kind kind;
        String detail;
        if (loan != null && loan.status().isActive()) {
            kind = MasterAlert.Kind.FROZEN_WHILE_OUT;
            detail = "Gel posé alors que la boîte est sortie en prêt"
                + (loan.expectedReturnAt() == null ? "" : " (retour prévu le " + DAY.withZone(clock.getZone()).format(loan.expectedReturnAt()) + ")")
                + " : demander son retour. Toute nouvelle sortie est bloquée.";
        } else if (loan != null) {
            kind = MasterAlert.Kind.FROZEN_WITH_REQUEST;
            detail = "Gel posé alors qu'une demande de prêt attend : la sortie sera refusée, prévenir le demandeur.";
        } else {
            kind = MasterAlert.Kind.FROZEN;
            detail = "Gel posé : toute sortie est bloquée.";
        }
        if (!alerts.insertIfAbsent(MasterAlert.raise(tenant, kind, cote.value(), record.id(), ev, detail, clock.instant()))) {
            return false;
        }
        if (record.status() != PhysicalArchiveStatus.FROZEN) {
            legalHold.execute(tenant, cote, ApplyLegalHoldUseCase.Action.FREEZE, ACTOR, note(ev));
        }
        return true;
    }

    private boolean onUnfrozen(TenantId tenant, MasterEvent ev, Cote cote) {
        PhysicalArchiveRecord record = records.findByCote(tenant, cote).orElse(null);
        if (record == null) return false;
        if (!alerts.insertIfAbsent(MasterAlert.raise(tenant, MasterAlert.Kind.UNFROZEN, cote.value(), record.id(), ev,
            "Gel levé : les sorties sont de nouveau possibles.", clock.instant()))) {
            return false;
        }
        if (record.status() == PhysicalArchiveStatus.FROZEN) {
            legalHold.execute(tenant, cote, ApplyLegalHoldUseCase.Action.RELEASE, ACTOR, note(ev));
        }
        return true;
    }

    private static String note(MasterEvent ev) {
        return "evenement-maitre=" + ev.id() + "; par=" + ev.actor();
    }

    /** Même texte, aux espaces près (un formulaire peut les retoucher). */
    public static boolean same(String a, String b) {
        return normalize(a).equals(normalize(b));
    }

    private static String normalize(String s) {
        return s == null ? "" : s.strip().replaceAll("\\s+", " ");
    }

    // ─── Consultation ────────────────────────────────────────────────────────────

    public Status status(TenantId tenant) {
        boolean available = master.publishesEvents(tenant);
        MasterWatchState s = tx.execute(t -> states.find(tenant)).orElse(null);
        int open = available ? count(tenant) : 0;
        if (s == null) return new Status(available, null, null, null, null, 0, open);
        return new Status(available, s.lastRunAt(), s.lastSuccessAt(), s.lastError(), s.cursorAt(), s.lastEvents(), open);
    }

    private int count(TenantId tenant) {
        Integer n = tx.execute(t -> alerts.countOpenActions(tenant));
        return n == null ? 0 : n;
    }

    public List<MasterAlert> alerts(TenantId tenant, boolean onlyOpenActions, int limit) {
        return tx.execute(t -> alerts.list(tenant, onlyOpenActions, Math.max(1, Math.min(limit, 500))));
    }

    /** Marque traité ; faux si inconnu ou déjà traité. Audité. */
    public boolean acknowledge(TenantId tenant, UUID id, String by) {
        return Boolean.TRUE.equals(TenantContext.callWithUnchecked(tenant, () -> tx.execute(t -> {
            if (!alerts.acknowledge(tenant, id, by, clock.instant())) return false;
            audit.record(AuditAction.MASTER_ALERT_ACKNOWLEDGED, AuditTargetType.MASTER_ALERT, id, by, "signalement traité");
            return true;
        })));
    }
}
