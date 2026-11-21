package com.jokkorm.application.record;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.common.Page;
import com.jokkorm.domain.common.PageRequest;
import com.jokkorm.domain.record.PhysicalArchiveRecord;
import com.jokkorm.domain.record.PhysicalArchiveRecordRepository;
import com.jokkorm.domain.record.PhysicalArchiveStatus;
import org.springframework.stereotype.Service;

/** Liste paginée, filtre optionnel sur statut. */
@Service
public class ListRecordsUseCase {

    private final PhysicalArchiveRecordRepository repository;

    public ListRecordsUseCase(PhysicalArchiveRecordRepository repository) {
        this.repository = repository;
    }

    public Page<PhysicalArchiveRecord> execute(
        TenantId tenant, PhysicalArchiveStatus statusFilter, PageRequest pageRequest) {
        return repository.findAllByStatus(tenant, statusFilter, pageRequest);
    }
}
