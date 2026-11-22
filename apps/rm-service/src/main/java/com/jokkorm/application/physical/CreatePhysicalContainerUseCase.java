package com.jokkorm.application.physical;

import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.physical.PhysicalContainer;
import com.jokkorm.domain.physical.PhysicalContainerRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crée une nouvelle boîte vide non positionnée. Le code-barres est TOUJOURS généré
 * automatiquement par {@link BarcodeGenerator} — on n'accepte plus de code saisi
 * côté client pour garantir l'unicité, le format normalisé, et éviter l'incohérence
 * (phase 5K).
 */
@Service
public class CreatePhysicalContainerUseCase {

    private final PhysicalContainerRepository repository;
    private final BarcodeGenerator barcodeGenerator;
    private final Clock clock;

    public CreatePhysicalContainerUseCase(
        PhysicalContainerRepository repository,
        BarcodeGenerator barcodeGenerator,
        Clock clock) {
        this.repository = repository;
        this.barcodeGenerator = barcodeGenerator;
        this.clock = clock;
    }

    @Transactional
    public PhysicalContainer execute(String label, Integer capacity) {
        TenantId tenant = TenantContext.current();
        String barcode = barcodeGenerator.generateForContainer(tenant);
        return repository.save(
            PhysicalContainer.createEmpty(tenant, barcode, label, capacity, clock.instant()));
    }
}
