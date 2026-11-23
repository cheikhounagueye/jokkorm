package com.jokkorm.application.record;

import com.jokkorm.commons.role.RoleContext;
import com.jokkorm.commons.role.UserRole;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.master.MasterArchiveView;
import com.jokkorm.domain.master.SaeMasterPort;
import com.jokkorm.domain.record.Cote;
import com.jokkorm.domain.record.PhysicalArchiveRecord;
import com.jokkorm.domain.record.PhysicalArchiveRecordRepository;
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

    public ImportFromMasterUseCase(SaeMasterPort master, PhysicalArchiveRecordRepository records,
                                   HandleCoteAssignedEventUseCase register) {
        this.master = master;
        this.records = records;
        this.register = register;
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
        return new Result(record, view, !existed);
    }
}
