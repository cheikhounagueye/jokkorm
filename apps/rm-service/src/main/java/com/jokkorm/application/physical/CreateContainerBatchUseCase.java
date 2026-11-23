package com.jokkorm.application.physical;

import com.jokkorm.application.audit.AuditChainService;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.audit.AuditAction;
import com.jokkorm.domain.audit.AuditTargetType;
import com.jokkorm.domain.physical.ContainerBatch;
import com.jokkorm.domain.physical.ContainerBatchRepository;
import com.jokkorm.domain.physical.PhysicalContainer;
import com.jokkorm.domain.physical.PhysicalContainerRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crée un lot de N boîtes en une transaction : N barcodes uniques générés + N containers
 * en transit (positionId=null), avec numérotation séquentielle 1..N dans le lot.
 * Voir docs/pattern/06-provisioning-masse-boites.md.
 */
@Service
public class CreateContainerBatchUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(CreateContainerBatchUseCase.class);

    private final ContainerBatchRepository batches;
    private final PhysicalContainerRepository containers;
    private final BarcodeGenerator barcodeGenerator;
    private final AuditChainService audit;
    private final BoxFormatCatalog catalog;
    private final Clock clock;

    public CreateContainerBatchUseCase(
        ContainerBatchRepository batches,
        PhysicalContainerRepository containers,
        BarcodeGenerator barcodeGenerator,
        AuditChainService audit,
        BoxFormatCatalog catalog,
        Clock clock) {
        this.batches = batches;
        this.containers = containers;
        this.barcodeGenerator = barcodeGenerator;
        this.audit = audit;
        this.catalog = catalog;
        this.clock = clock;
    }

    public record Result(ContainerBatch batch, List<PhysicalContainer> containers) {}

    @Transactional
    public Result execute(String reference, int size, String labelPattern,
                          Integer defaultCapacity, String createdBy) {
        return execute(reference, size, labelPattern, defaultCapacity, null, createdBy);
    }

    /** Toutes les boîtes d'un lot ont le même format ({@code null} = défaut du tenant). */
    @Transactional
    public Result execute(String reference, int size, String labelPattern,
                          Integer defaultCapacity, java.util.UUID boxFormatId, String createdBy) {
        TenantId tenant = TenantContext.current();
        java.time.Instant now = clock.instant();
        var format = catalog.resolveForNewBox(tenant, boxFormatId);

        ContainerBatch batch = batches.save(ContainerBatch.create(
            tenant, reference, size, labelPattern, defaultCapacity, createdBy, now));

        List<PhysicalContainer> created = new ArrayList<>(size);
        for (int i = 1; i <= size; i++) {
            String barcode = barcodeGenerator.generateForContainer(tenant);
            String label = labelPattern == null
                ? null
                : labelPattern + " · " + String.format("%03d/%03d", i, size);
            PhysicalContainer c = PhysicalContainer.createInBatch(
                tenant, barcode, label, defaultCapacity, batch.id(), i, format.id(), now);
            created.add(containers.save(c));
        }

        audit.record(
            AuditAction.CONTAINER_BATCH_CREATED,
            AuditTargetType.CONTAINER_BATCH,
            batch.id(),
            createdBy,
            "reference=%s size=%d labelPattern=%s format=%s"
                .formatted(reference, size, labelPattern == null ? "" : labelPattern, format.code()));

        LOG.info("Container batch created : ref='{}' size={} actor={}", reference, size, createdBy);
        return new Result(batch, created);
    }
}
