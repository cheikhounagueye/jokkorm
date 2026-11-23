package com.jokkorm.application.physical;

import com.jokkorm.application.audit.AuditChainService;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.audit.AuditAction;
import com.jokkorm.domain.audit.AuditTargetType;
import com.jokkorm.domain.physical.BoxFormat;
import com.jokkorm.domain.physical.PhysicalContainer;
import com.jokkorm.domain.physical.PhysicalContainerRepository;
import com.jokkorm.domain.physical.StorageLocationRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Corrige le format d'une boîte (ex. une boîte enregistrée dos 10 qui est en réalité un
 * dos 15). Si elle est rangée, le nouveau dos doit tenir là où elle est.
 */
@Service
public class ChangeContainerFormatUseCase {

    private final PhysicalContainerRepository containers;
    private final StorageLocationRepository locations;
    private final BoxFormatCatalog catalog;
    private final ShelfSpaceService space;
    private final AuditChainService audit;
    private final Clock clock;

    public ChangeContainerFormatUseCase(
        PhysicalContainerRepository containers,
        StorageLocationRepository locations,
        BoxFormatCatalog catalog,
        ShelfSpaceService space,
        AuditChainService audit,
        Clock clock) {
        this.containers = containers;
        this.locations = locations;
        this.catalog = catalog;
        this.space = space;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public PhysicalContainer execute(UUID containerId, UUID boxFormatId, String actor) {
        TenantId tenant = TenantContext.current();
        PhysicalContainer box = containers.findById(tenant, containerId)
            .orElseThrow(() -> new PhysicalContainerNotFoundException(
                "Container %s not found (tenant=%s)".formatted(containerId, tenant.value())));
        if (box.boxFormatId().filter(boxFormatId::equals).isPresent()) return box;

        BoxFormat target = catalog.resolveForNewBox(tenant, boxFormatId);
        String previous = catalog.spines(tenant).formatOf(box).map(BoxFormat::code).orElse("défaut");

        box.changeFormat(target.id(), clock.instant());
        if (box.positionId().isPresent()) {
            var position = locations.findById(tenant, box.positionId().get())
                .orElseThrow(() -> new StorageLocationNotFoundException(
                    "Position %s introuvable".formatted(box.positionId().get())));
            space.requireRoomFor(tenant, position, box);
        }
        PhysicalContainer saved = containers.save(box);
        audit.record(AuditAction.CONTAINER_FORMAT_CHANGED, AuditTargetType.CONTAINER, saved.id(), actor,
            "barcode=%s from=%s to=%s".formatted(saved.barcode(), previous, target.code()));
        return saved;
    }
}
