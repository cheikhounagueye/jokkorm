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
 * (phase 5K). Le libellé est nettoyé et ne doit être porté par aucune autre boîte de
 * l'espace ({@link ContainerLabelGuard}).
 */
@Service
public class CreatePhysicalContainerUseCase {

    private final PhysicalContainerRepository repository;
    private final BarcodeGenerator barcodeGenerator;
    private final BoxFormatCatalog catalog;
    private final ContainerLabelGuard labels;
    private final Clock clock;

    public CreatePhysicalContainerUseCase(
        PhysicalContainerRepository repository,
        BarcodeGenerator barcodeGenerator,
        BoxFormatCatalog catalog,
        ContainerLabelGuard labels,
        Clock clock) {
        this.repository = repository;
        this.labels = labels;
        this.barcodeGenerator = barcodeGenerator;
        this.catalog = catalog;
        this.clock = clock;
    }

    /** {@code boxFormatId} null = format par défaut du tenant (pattern 10). */
    @Transactional
    public PhysicalContainer execute(String label, Integer capacity, java.util.UUID boxFormatId) {
        TenantId tenant = TenantContext.current();
        String cleanLabel = ContainerLabelGuard.clean(label);
        labels.requireFree(tenant, cleanLabel);
        var format = catalog.resolveForNewBox(tenant, boxFormatId);
        String barcode = barcodeGenerator.generateForContainer(tenant);
        return repository.save(
            PhysicalContainer.createEmpty(tenant, barcode, cleanLabel, capacity, format.id(), clock.instant()));
    }
}
