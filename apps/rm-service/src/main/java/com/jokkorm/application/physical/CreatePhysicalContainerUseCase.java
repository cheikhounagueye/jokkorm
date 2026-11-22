package com.jokkorm.application.physical;

import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.physical.PhysicalContainer;
import com.jokkorm.domain.physical.PhysicalContainerRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Crée une nouvelle boîte vide non positionnée. */
@Service
public class CreatePhysicalContainerUseCase {

    private final PhysicalContainerRepository repository;
    private final Clock clock;

    public CreatePhysicalContainerUseCase(PhysicalContainerRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public PhysicalContainer execute(String barcode, String label, Integer capacity) {
        TenantId tenant = TenantContext.current();
        String normalized = barcode == null ? null : barcode.toUpperCase(java.util.Locale.ROOT).trim();
        if (normalized != null && repository.existsByBarcode(tenant, normalized)) {
            throw new PhysicalContainerConflictException(
                "A container with barcode '%s' already exists for tenant %s"
                    .formatted(normalized, tenant.value()));
        }
        return repository.save(
            PhysicalContainer.createEmpty(tenant, barcode, label, capacity, clock.instant()));
    }
}
