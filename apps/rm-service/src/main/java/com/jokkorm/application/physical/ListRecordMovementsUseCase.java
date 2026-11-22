package com.jokkorm.application.physical;

import com.jokkorm.application.record.RecordNotFoundException;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.physical.PhysicalMovement;
import com.jokkorm.domain.physical.PhysicalMovementRepository;
import com.jokkorm.domain.record.Cote;
import com.jokkorm.domain.record.PhysicalArchiveRecordRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Timeline par cote. */
@Service
public class ListRecordMovementsUseCase {

    private final PhysicalArchiveRecordRepository records;
    private final PhysicalMovementRepository movements;

    public ListRecordMovementsUseCase(
        PhysicalArchiveRecordRepository records,
        PhysicalMovementRepository movements) {
        this.records = records;
        this.movements = movements;
    }

    @Transactional(readOnly = true)
    public List<PhysicalMovement> execute(Cote cote) {
        TenantId tenant = TenantContext.current();
        var record = records.findByCote(tenant, cote)
            .orElseThrow(() -> new RecordNotFoundException(
                "No physical archive record for cote=%s (tenant=%s)"
                    .formatted(cote.value(), tenant.value())));
        return movements.findByRecord(tenant, record.id());
    }
}
