package com.jokkorm.application.physical;

import com.jokkorm.application.audit.AuditChainService;
import com.jokkorm.commons.role.RoleContext;
import com.jokkorm.commons.role.UserRole;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.audit.AuditAction;
import com.jokkorm.domain.audit.AuditTargetType;
import com.jokkorm.domain.physical.RoomLimits;
import com.jokkorm.domain.physical.RoomSpec;
import com.jokkorm.domain.physical.RoomSpecRepository;
import com.jokkorm.domain.physical.StorageLocation;
import com.jokkorm.domain.physical.StorageLocationKind;
import com.jokkorm.domain.physical.StorageLocationRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dimensions et limites d'une salle, et ce que le mobilier en occupe (pattern 10 § 2.4). */
@Service
public class RoomSpecUseCase {

    /** Ce qui est saisi (null si rien) et le contrôle du mobilier face à la salle. */
    public record View(Input spec, RoomLimits.Report limits) {}

    /** Valeurs saisies ; toutes facultatives. */
    public record Input(Integer lengthMm, Integer widthMm, Integer ceilingHeightMm, Double usableAreaM2,
                        Integer floorLoadKgM2, Double tempMinC, Double tempMaxC, Integer rhMinPct, Integer rhMaxPct) {
        static Input of(RoomSpec s) {
            return s == null ? null : new Input(s.lengthMm(), s.widthMm(), s.ceilingHeightMm(), s.usableAreaM2(),
                s.floorLoadKgM2(), s.tempMinC(), s.tempMaxC(), s.rhMinPct(), s.rhMaxPct());
        }
    }

    private final StorageLocationRepository locations;
    private final RoomSpecRepository specs;
    private final ShelfSpaceService space;
    private final AuditChainService audit;
    private final Clock clock;

    public RoomSpecUseCase(StorageLocationRepository locations, RoomSpecRepository specs, ShelfSpaceService space,
                           AuditChainService audit, Clock clock) {
        this.locations = locations;
        this.specs = specs;
        this.space = space;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public View get(UUID storehouseId) {
        TenantId tenant = TenantContext.current();
        StorageLocation room = requireRoom(tenant, storehouseId);
        RoomSpec spec = specs.findByStorehouse(tenant, room.id()).orElse(null);
        return new View(Input.of(spec), limits(tenant, room, spec));
    }

    @Transactional
    public View set(UUID storehouseId, Input in, String actor) {
        RoleContext.requireAny(UserRole.ARCHIVIST, UserRole.ARCHIVES_HEAD);
        TenantId tenant = TenantContext.current();
        StorageLocation room = requireRoom(tenant, storehouseId);
        RoomSpec saved = specs.save(new RoomSpec(room.id(), tenant, in.lengthMm(), in.widthMm(), in.ceilingHeightMm(),
            in.usableAreaM2(), in.floorLoadKgM2(), in.tempMinC(), in.tempMaxC(), in.rhMinPct(), in.rhMaxPct()),
            clock.instant());
        audit.record(AuditAction.ROOM_SPEC_SET, AuditTargetType.STORAGE_LOCATION, room.id(), actor,
            "path=%s spec=%s".formatted(room.path(), saved));
        return new View(Input.of(saved), limits(tenant, room, saved));
    }

    private RoomLimits.Report limits(TenantId tenant, StorageLocation room, RoomSpec spec) {
        var shelves = locations.findChildren(tenant, room.id()).stream()
            .filter(l -> l.kind() == StorageLocationKind.SHELF).toList();
        return RoomLimits.check(spec, shelves, s -> s.lengthMm().orElse(space.defaultTabletteMm()));
    }

    private StorageLocation requireRoom(TenantId tenant, UUID id) {
        StorageLocation room = locations.findById(tenant, id)
            .orElseThrow(() -> new StorageLocationNotFoundException("Salle %s introuvable".formatted(id)));
        if (room.kind() != StorageLocationKind.STOREHOUSE) {
            throw new IllegalArgumentException("Seule une salle (magasin) a des dimensions de salle");
        }
        return room;
    }
}
