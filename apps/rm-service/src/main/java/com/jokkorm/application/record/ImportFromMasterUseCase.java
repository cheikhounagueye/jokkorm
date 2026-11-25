package com.jokkorm.application.record;

import com.jokkorm.application.audit.AuditChainService;
import com.jokkorm.application.master.MasterSnapshotService;
import com.jokkorm.commons.role.RoleContext;
import com.jokkorm.commons.role.UserRole;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.audit.AuditAction;
import com.jokkorm.domain.audit.AuditTargetType;
import com.jokkorm.domain.master.MasterArchiveView;
import com.jokkorm.domain.master.MasterImportCandidateRepository;
import com.jokkorm.domain.master.SaeMasterPort;
import com.jokkorm.domain.record.Cote;
import com.jokkorm.domain.record.PhysicalArchiveRecord;
import com.jokkorm.domain.record.PhysicalArchiveRecordRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * L'archiviste fait entrer dans JokkoRM une archive connue du maître (Maarch RM ne prévient
 * pas JokkoRM quand il attribue une cote : pas de webhook). On vérifie d'abord que le
 * maître connaît la cote, puis on crée l'enveloppe physique comme l'aurait fait
 * l'événement {@code cote-assigned} : même use case, donc idempotent et audité.
 *
 * <p>Seules les archives qui ont du papier sont à importer : c'est un geste choisi,
 * pas une copie du catalogue du maître (patterns 01 et 04).
 */
@Service
public class ImportFromMasterUseCase {

    public record Result(PhysicalArchiveRecord record, MasterArchiveView master, boolean created) {}

    private final SaeMasterPort master;
    private final PhysicalArchiveRecordRepository records;
    private final HandleCoteAssignedEventUseCase register;
    private final MasterImportCandidateRepository candidates;
    private final AuditChainService audit;
    private final Clock clock;
    private final MasterSnapshotService snapshots;

    public ImportFromMasterUseCase(SaeMasterPort master, PhysicalArchiveRecordRepository records,
                                   HandleCoteAssignedEventUseCase register, MasterImportCandidateRepository candidates,
                                   AuditChainService audit, Clock clock, MasterSnapshotService snapshots) {
        this.snapshots = snapshots;
        this.master = master;
        this.records = records;
        this.register = register;
        this.candidates = candidates;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public Result execute(Cote cote, String actor) {
        RoleContext.requireAny(UserRole.ARCHIVIST, UserRole.ARCHIVES_HEAD);
        TenantId tenant = TenantContext.current();
        MasterArchiveView view = master.fetchByCote(tenant, cote)
            .orElseThrow(() -> new RecordNotFoundException(
                "La cote " + cote.value() + " est inconnue de la GED maître"));
        boolean existed = records.findByCote(tenant, cote).isPresent();
        PhysicalArchiveRecord record = register.execute(tenant, cote, actor);
        // D3 : copie de rangement prise dans la réponse qu'on vient de lire (pas de second appel).
        snapshots.record(tenant, record.id(), view);
        // Qu'il vienne de la liste « à importer » ou de la recherche, l'import solde la candidate (étape 2).
        candidates.markImportedByCote(tenant, cote.value(), actor, clock.instant()).ifPresent(c ->
            audit.record(AuditAction.MASTER_CANDIDATE_IMPORTED, AuditTargetType.PHYSICAL_ARCHIVE_RECORD, record.id(),
                actor, "cote=" + cote.value() + "; profil=" + c.profileReference() + "; evenement-maitre="
                    + c.masterEventId() + "; depose-par=" + c.depositedBy()));
        return new Result(record, view, !existed);
    }
}
