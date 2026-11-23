package com.jokkorm.application.physical;

import com.jokkorm.application.audit.AuditChainService;
import com.jokkorm.commons.role.RoleContext;
import com.jokkorm.commons.role.UserRole;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.audit.AuditAction;
import com.jokkorm.domain.audit.AuditTargetType;
import com.jokkorm.domain.physical.PhysicalSpec;
import com.jokkorm.domain.physical.StorageLocation;
import com.jokkorm.domain.physical.StorageLocationKind;
import com.jokkorm.domain.physical.StorageLocationRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mobilier et dimensions d'une travée, ou dimensions propres d'une tablette (pattern 10, 5T.B).
 * Refuse une hauteur libre ou une profondeur dans laquelle une boîte déjà rangée ne tiendrait
 * plus. Une travée transmet ses valeurs aux tablettes qui n'ont pas les leurs : on contrôle
 * donc aussi ces tablettes. Écrit puis contrôle dans la même transaction : un refus annule tout.
 */
@Service
public class SetStorageSpecUseCase {

    private final StorageLocationRepository locations;
    private final ShelfSpaceService space;
    private final AuditChainService audit;
    private final Clock clock;

    public SetStorageSpecUseCase(StorageLocationRepository locations, ShelfSpaceService space,
                                 AuditChainService audit, Clock clock) {
        this.locations = locations;
        this.space = space;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public StorageLocation execute(UUID locationId, PhysicalSpec spec, String actor) {
        RoleContext.requireAny(UserRole.ARCHIVIST, UserRole.ARCHIVES_HEAD);
        TenantId tenant = TenantContext.current();
        StorageLocation node = locations.lockById(tenant, locationId)
            .orElseThrow(() -> new StorageLocationNotFoundException(
                "Storage location %s not found (tenant=%s)".formatted(locationId, tenant.value())));
        if (!StorageLocation.hasSpec(node.kind())) {
            throw new IllegalArgumentException("Seules la travée et la tablette ont un mobilier ou des dimensions");
        }
        PhysicalSpec previous = node.spec();
        node.changeSpec(spec, clock.instant());
        StorageLocation saved = locations.save(node);

        if (saved.kind() == StorageLocationKind.RACK) {
            requireBoxesFit(space.tablette(tenant, saved));
        } else {
            locations.findChildren(tenant, saved.id()).stream()
                .filter(r -> r.kind() == StorageLocationKind.RACK)
                .forEach(r -> requireBoxesFit(space.tablette(tenant, r)));
        }

        audit.record(AuditAction.STORAGE_LOCATION_SPEC_SET, AuditTargetType.STORAGE_LOCATION, saved.id(), actor,
            "path=%s previous=%s new=%s".formatted(saved.path(), previous, saved.spec()));
        return saved;
    }

    private static void requireBoxesFit(ShelfSpaceService.TabletteSpace t) {
        t.boxes().stream().map(t::sizeProblem).flatMap(java.util.Optional::stream).findFirst()
            .ifPresent(problem -> {
                throw new StorageLocationConflictException(problem + " (boîte déjà rangée : réglage refusé)");
            });
    }
}
