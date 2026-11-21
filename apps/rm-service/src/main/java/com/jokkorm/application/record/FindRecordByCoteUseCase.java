package com.jokkorm.application.record;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.record.Cote;
import com.jokkorm.domain.record.PhysicalArchiveRecord;
import com.jokkorm.domain.record.PhysicalArchiveRecordRepository;
import org.springframework.stereotype.Service;

/** Lecture simple par cote, lève {@link RecordNotFoundException} si absent. */
@Service
public class FindRecordByCoteUseCase {

    private final PhysicalArchiveRecordRepository repository;

    public FindRecordByCoteUseCase(PhysicalArchiveRecordRepository repository) {
        this.repository = repository;
    }

    public PhysicalArchiveRecord execute(TenantId tenant, Cote cote) {
        return repository.findByCote(tenant, cote)
            .orElseThrow(() -> new RecordNotFoundException(
                "No physical archive record for cote=%s (tenant=%s)"
                    .formatted(cote.value(), tenant.value())));
    }
}
