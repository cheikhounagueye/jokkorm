package com.jokkorm.application.physical;

import com.jokkorm.application.audit.AuditChainService;
import com.jokkorm.commons.role.RoleContext;
import com.jokkorm.commons.role.UserRole;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.audit.AuditAction;
import com.jokkorm.domain.audit.AuditTargetType;
import com.jokkorm.domain.physical.LinearCapacity;
import com.jokkorm.domain.physical.StorageLocation;
import com.jokkorm.domain.physical.StorageLocationKind;
import com.jokkorm.domain.physical.StorageLocationRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fixe la longueur utile d'une travée, d'une tablette ou d'un emplacement (pattern 10).
 * Refuse toute longueur qui rendrait le magasin incohérent : plus courte que les boîtes
 * déjà rangées, ou emplacements plus longs que leur tablette. La nouvelle valeur est
 * écrite puis contrôlée dans la même transaction : un refus annule tout.
 */
@Service
public class SetStorageLengthUseCase {

    private final StorageLocationRepository locations;
    private final ShelfSpaceService space;
    private final AuditChainService audit;
    private final Clock clock;

    public SetStorageLengthUseCase(
        StorageLocationRepository locations,
        ShelfSpaceService space,
        AuditChainService audit,
        Clock clock) {
        this.locations = locations;
        this.space = space;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public StorageLocation execute(UUID locationId, Integer lengthMm, String actor) {
        RoleContext.requireAny(UserRole.ARCHIVIST, UserRole.ARCHIVES_HEAD);
        TenantId tenant = TenantContext.current();
        StorageLocation node = locations.lockById(tenant, locationId)
            .orElseThrow(() -> new StorageLocationNotFoundException(
                "Storage location %s not found (tenant=%s)".formatted(locationId, tenant.value())));
        if (!StorageLocation.hasLength(node.kind())) {
            throw new IllegalArgumentException("Un " + node.kind() + " n'a pas de longueur de rangement");
        }
        String previous = node.lengthMm().map(String::valueOf).orElse("déduite");
        node.changeLength(lengthMm, clock.instant());
        StorageLocation saved = locations.save(node);

        switch (saved.kind()) {
            case POSITION -> checkTablette(space.tabletteOf(tenant, saved, false, null));
            case RACK -> checkTablette(space.tablette(tenant, saved));
            case SHELF -> locations.findChildren(tenant, saved.id()).stream()
                .filter(r -> r.kind() == StorageLocationKind.RACK && r.lengthMm().isEmpty())
                .forEach(r -> checkTablette(space.tablette(tenant, r)));
            default -> { }
        }

        audit.record(AuditAction.STORAGE_LOCATION_LENGTH_SET, AuditTargetType.STORAGE_LOCATION,
            saved.id(), actor, "path=%s previous=%s new=%s".formatted(saved.path(), previous,
                saved.lengthMm().map(String::valueOf).orElse("déduite")));
        return saved;
    }

    private void checkTablette(ShelfSpaceService.TabletteSpace t) {
        String path = t.tablette() == null ? "?" : t.tablette().path();
        int explicit = t.positions().stream().mapToInt(p -> p.lengthMm().orElse(0)).sum();
        if (explicit > t.lengthMm()) {
            throw new StorageLocationConflictException(
                "Tablette %s : ses emplacements totaliseraient %s pour une tablette de %s"
                    .formatted(path, LinearCapacity.cm(explicit), LinearCapacity.cm(t.lengthMm())));
        }
        if (t.usedMm() > t.lengthMm()) {
            throw new StorageLocationConflictException(
                "Tablette %s : les boîtes rangées occupent déjà %s, impossible de la réduire à %s"
                    .formatted(path, LinearCapacity.cm(t.usedMm()), LinearCapacity.cm(t.lengthMm())));
        }
        for (StorageLocation p : t.positions()) {
            int used = t.usedMm(p.id());
            int length = t.positionLengthMm(p);
            if (used > length) {
                throw new StorageLocationConflictException(
                    "Emplacement %s : ses boîtes occupent déjà %s, il ne mesurerait plus que %s"
                        .formatted(p.path(), LinearCapacity.cm(used), LinearCapacity.cm(length)));
            }
        }
    }
}
