package com.jokkorm.adapter.out.persistence.physical;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.physical.RoomSpec;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "room_spec")
public class RoomSpecEntity {

    @Id
    @Column(name = "storehouse_id", nullable = false)
    private UUID storehouseId;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "length_mm")
    private Integer lengthMm;

    @Column(name = "width_mm")
    private Integer widthMm;

    @Column(name = "ceiling_height_mm")
    private Integer ceilingHeightMm;

    @Column(name = "usable_area_m2", precision = 10, scale = 2)
    private BigDecimal usableAreaM2;

    @Column(name = "floor_load_kg_m2")
    private Integer floorLoadKgM2;

    @Column(name = "temp_min_c", precision = 4, scale = 1)
    private BigDecimal tempMinC;

    @Column(name = "temp_max_c", precision = 4, scale = 1)
    private BigDecimal tempMaxC;

    @Column(name = "rh_min_pct")
    private Integer rhMinPct;

    @Column(name = "rh_max_pct")
    private Integer rhMaxPct;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected RoomSpecEntity() { /* JPA */ }

    static RoomSpecEntity create(RoomSpec spec, Instant at) {
        RoomSpecEntity e = new RoomSpecEntity();
        e.storehouseId = spec.storehouseId();
        e.tenantId = spec.tenant().value();
        e.createdAt = at;
        e.copy(spec, at);
        return e;
    }

    void copy(RoomSpec s, Instant at) {
        lengthMm = s.lengthMm();
        widthMm = s.widthMm();
        ceilingHeightMm = s.ceilingHeightMm();
        usableAreaM2 = s.usableAreaM2() == null ? null : BigDecimal.valueOf(s.usableAreaM2());
        floorLoadKgM2 = s.floorLoadKgM2();
        tempMinC = s.tempMinC() == null ? null : BigDecimal.valueOf(s.tempMinC());
        tempMaxC = s.tempMaxC() == null ? null : BigDecimal.valueOf(s.tempMaxC());
        rhMinPct = s.rhMinPct();
        rhMaxPct = s.rhMaxPct();
        updatedAt = at;
    }

    RoomSpec toDomain() {
        return new RoomSpec(storehouseId, TenantId.of(tenantId), lengthMm, widthMm, ceilingHeightMm,
            usableAreaM2 == null ? null : usableAreaM2.doubleValue(), floorLoadKgM2,
            tempMinC == null ? null : tempMinC.doubleValue(), tempMaxC == null ? null : tempMaxC.doubleValue(),
            rhMinPct, rhMaxPct);
    }
}
