package com.jokkorm.application.master;

import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.loan.PhysicalLoanRepository;
import com.jokkorm.domain.master.MasterRejectedException;
import com.jokkorm.domain.master.MasterSync;
import com.jokkorm.domain.master.MasterSyncRepository;
import com.jokkorm.domain.master.MasterUnreachableException;
import com.jokkorm.domain.master.PhysicalStatus;
import com.jokkorm.domain.master.SaeMasterPort;
import com.jokkorm.domain.physical.ContainerState;
import com.jokkorm.domain.physical.PhysicalContainer;
import com.jokkorm.domain.physical.PhysicalContainerRepository;
import com.jokkorm.domain.physical.StorageLocation;
import com.jokkorm.domain.physical.StorageLocationRepository;
import com.jokkorm.domain.record.Cote;
import com.jokkorm.domain.record.PhysicalArchiveRecord;
import com.jokkorm.domain.record.PhysicalArchiveRecordRepository;
import com.jokkorm.domain.record.PhysicalArchiveStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Renvoi de l'état physique au maître (tranche 2a, pattern 03) par une file d'envoi.
 *
 * <ul>
 *   <li>Les gestes physiques appellent {@link #touchRecord} / {@link #touchContainer} dans leur
 *       transaction : une ligne « à envoyer » par cote, rien d'autre. Une panne du maître ne
 *       bloque jamais un rangement.</li>
 *   <li>{@link #drain} (appelé par un travailleur planifié) recalcule l'état ACTUEL de chaque cote
 *       et l'écrit chez le maître : la plus récente demande l'emporte, aucun état périmé.</li>
 *   <li>Panne → nouvel essai (30 s, 2 min, 10 min, 30 min, 2 h, puis toutes les 6 h) ; refus
 *       durable → « refusé » avec la raison, jusqu'au prochain geste sur la cote.</li>
 * </ul>
 */
@Service
public class MasterSyncService {

    private static final Logger LOG = LoggerFactory.getLogger(MasterSyncService.class);
    private static final Duration[] BACKOFF = {
        Duration.ofSeconds(30), Duration.ofMinutes(2), Duration.ofMinutes(10), Duration.ofMinutes(30),
        Duration.ofHours(2), Duration.ofHours(6)};
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final MasterSyncRepository queue;
    private final SaeMasterPort master;
    private final PhysicalArchiveRecordRepository records;
    private final PhysicalContainerRepository containers;
    private final StorageLocationRepository locations;
    private final PhysicalLoanRepository loans;
    private final TransactionTemplate tx;
    private final Clock clock;

    public MasterSyncService(MasterSyncRepository queue, SaeMasterPort master, PhysicalArchiveRecordRepository records,
                             PhysicalContainerRepository containers, StorageLocationRepository locations,
                             PhysicalLoanRepository loans, TransactionTemplate tx, Clock clock) {
        this.queue = queue;
        this.master = master;
        this.records = records;
        this.containers = containers;
        this.locations = locations;
        this.loans = loans;
        this.tx = tx;
        this.clock = clock;
    }

    // ─── Demandes d'envoi (dans la transaction du geste) ─────────────────────────

    /** L'état physique de cette archive a changé. */
    public void touchRecord(TenantId tenant, Cote cote) {
        if (master.acceptsPhysicalStatus(tenant)) {
            queue.touch(tenant, cote.value(), clock.instant());
        }
    }

    /** La boîte a bougé (rangement, déplacement, prêt, retour) : toutes ses archives. */
    public void touchContainer(TenantId tenant, UUID containerId) {
        if (!master.acceptsPhysicalStatus(tenant)) return;
        Instant now = clock.instant();
        for (PhysicalArchiveRecord r : records.findByContainer(tenant, containerId)) {
            queue.touch(tenant, r.cote().value(), now);
        }
    }

    public Optional<MasterSync> status(Cote cote) {
        return queue.find(TenantContext.current(), cote.value());
    }

    // ─── Envoi (hors transaction des gestes) ─────────────────────────────────────

    /** Envoie les états dus ; renvoie le nombre de lignes traitées. */
    public int drain(int limit) {
        Instant now = clock.instant();
        List<MasterSync> due = tx.execute(s -> queue.findDue(now, limit));
        if (due == null) return 0;
        for (MasterSync item : due) {
            deliver(item);
        }
        return due.size();
    }

    private void deliver(MasterSync item) {
        TenantId tenant = item.tenant();
        Instant now = clock.instant();
        try {
            PhysicalStatus status = TenantContext.callWithUnchecked(tenant,
                () -> tx.execute(s -> resolve(tenant, Cote.of(item.cote()))));
            if (status == null) {
                tx.executeWithoutResult(s -> queue.markRejected(tenant, item.cote(), item.version(),
                    "Archive inconnue de JokkoRM", now));
                return;
            }
            TenantContext.runWith(tenant, () -> master.publishPhysicalStatus(tenant, Cote.of(item.cote()), status));
            boolean sent = Boolean.TRUE.equals(tx.execute(s ->
                queue.markSent(tenant, item.cote(), item.version(), status.line(), clock.instant())));
            if (!sent) LOG.debug("[master-sync] {} : nouveau geste pendant l'envoi, renvoi au prochain passage", item.cote());
        } catch (MasterRejectedException e) {
            LOG.warn("[master-sync] {} refusé par le maître : {}", item.cote(), e.getMessage());
            tx.executeWithoutResult(s -> queue.markRejected(tenant, item.cote(), item.version(), e.getMessage(), now));
        } catch (MasterUnreachableException e) {
            Duration wait = BACKOFF[Math.min(item.attempts(), BACKOFF.length - 1)];
            LOG.info("[master-sync] {} : maître injoignable ({}), nouvel essai dans {}", item.cote(), e.getMessage(), wait);
            tx.executeWithoutResult(s -> queue.markRetry(tenant, item.cote(), item.version(), e.getMessage(), now, now.plus(wait)));
        } catch (RuntimeException e) {
            LOG.error("[master-sync] {} : erreur inattendue", item.cote(), e);
            Duration wait = BACKOFF[Math.min(item.attempts(), BACKOFF.length - 1)];
            tx.executeWithoutResult(s -> queue.markRetry(tenant, item.cote(), item.version(),
                e.getClass().getSimpleName() + " : " + e.getMessage(), now, now.plus(wait)));
        }
    }

    // ─── État physique actuel d'une cote ─────────────────────────────────────────

    /** null si JokkoRM ne connaît pas la cote. */
    PhysicalStatus resolve(TenantId tenant, Cote cote) {
        ZoneId zone = clock.getZone();
        String asOf = WHEN.withZone(zone).format(clock.instant());
        PhysicalArchiveRecord record = records.findByCote(tenant, cote).orElse(null);
        if (record == null) return null;
        if (record.status() == PhysicalArchiveStatus.DESTROYED) return new PhysicalStatus(null, "détruite", asOf);
        if (record.status() == PhysicalArchiveStatus.TRANSFERRED) return new PhysicalStatus(null, "transférée", asOf);
        PhysicalContainer box = record.containerId().flatMap(id -> containers.findById(tenant, id)).orElse(null);
        if (box == null) return new PhysicalStatus(null, "pas encore rangée", asOf);
        if (box.state() == ContainerState.DESTROYED) return new PhysicalStatus(null, "boîte détruite", asOf);
        String address = box.positionId().flatMap(id -> locations.findById(tenant, id)).map(p -> address(tenant, p)).orElse(null);
        String state = loans.findActiveByContainer(tenant, box.id())
            .map(l -> l.expectedReturnAt() == null ? "en prêt"
                : "en prêt, retour prévu le " + DAY.withZone(zone).format(l.expectedReturnAt()))
            .orElse(address == null ? "en zone tampon (pas encore rangée)" : "en magasin");
        return new PhysicalStatus(address, state, asOf);
    }

    /** « SALLE-A · Épi E1 · rang 3 · face A · Travée T03 · Tablette 2 · Emplacement 01 » — sans code-barres. */
    private String address(TenantId tenant, StorageLocation position) {
        List<StorageLocation> chain = new ArrayList<>();
        StorageLocation cur = position;
        while (cur != null) {
            chain.add(0, cur);
            cur = cur.parentId().flatMap(id -> locations.findById(tenant, id)).orElse(null);
        }
        List<String> parts = new ArrayList<>();
        for (StorageLocation n : chain) {
            switch (n.kind()) {
                case SITE -> { }
                case STOREHOUSE -> parts.add(n.label() == null ? n.code() : n.label() + " (" + n.code() + ")");
                case SHELF -> {
                    var s = n.spec();
                    if (s.aisleCode() != null) {
                        parts.add("Épi " + s.aisleCode() + (s.aisleOrder() != null ? " · rang " + s.aisleOrder() : "")
                            + (s.aisleSide() != null ? " · face " + s.aisleSide() : ""));
                    }
                    parts.add("Travée " + n.code());
                }
                case RACK -> parts.add("Tablette " + n.code());
                case POSITION -> parts.add("Emplacement " + n.code());
            }
        }
        return String.join(" · ", parts);
    }

}
