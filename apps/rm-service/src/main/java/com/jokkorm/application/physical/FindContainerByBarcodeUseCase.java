package com.jokkorm.application.physical;

import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.physical.PhysicalContainer;
import com.jokkorm.domain.physical.PhysicalContainerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lookup par code-barres — le cas d'usage central de la douchette. */
@Service
public class FindContainerByBarcodeUseCase {

    private final PhysicalContainerRepository repository;

    public FindContainerByBarcodeUseCase(PhysicalContainerRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PhysicalContainer execute(String barcode) {
        TenantId tenant = TenantContext.current();
        String normalized = barcode == null ? "" : barcode.toUpperCase(java.util.Locale.ROOT).trim();
        return repository.findByBarcode(tenant, normalized)
            .orElseThrow(() -> new PhysicalContainerNotFoundException(
                "No container with barcode=%s (tenant=%s)".formatted(normalized, tenant.value())));
    }
}
