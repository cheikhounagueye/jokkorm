package com.jokkorm.adapter.out.persistence.physical;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.physical.BoxFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "box_format")
public class BoxFormatEntity {

    @Id @Column(name = "id", nullable = false) private UUID id;
    @Column(name = "tenant_id", length = 64, nullable = false) private String tenantId;
    @Column(name = "code", length = 32, nullable = false) private String code;
    @Column(name = "label", length = 255, nullable = false) private String label;
    @Column(name = "spine_mm", nullable = false) private int spineMm;
    @Column(name = "depth_mm", nullable = false) private int depthMm;
    @Column(name = "height_mm", nullable = false) private int heightMm;
    @Enumerated(EnumType.STRING) @Column(name = "orientation", length = 16, nullable = false) private BoxFormat.Orientation orientation;
    @Enumerated(EnumType.STRING) @Column(name = "quality", length = 32, nullable = false) private BoxFormat.Quality quality;
    @Column(name = "max_weight_kg") private Integer maxWeightKg;
    @Column(name = "active", nullable = false) private boolean active;
    @Column(name = "is_default", nullable = false) private boolean defaultFormat;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(name = "version", nullable = false) private long version;

    protected BoxFormatEntity() { /* JPA */ }

    public static BoxFormatEntity fromDomain(BoxFormat d) {
        BoxFormatEntity e = new BoxFormatEntity();
        e.id = d.id();
        e.tenantId = d.tenantId().value();
        e.code = d.code();
        e.copyMutableStateFrom(d);
        e.spineMm = d.spineMm();
        e.depthMm = d.depthMm();
        e.heightMm = d.heightMm();
        e.orientation = d.orientation();
        e.createdAt = d.createdAt();
        return e;
    }

    public void copyMutableStateFrom(BoxFormat d) {
        this.label = d.label();
        this.quality = d.quality();
        this.maxWeightKg = d.maxWeightKg().orElse(null);
        this.active = d.active();
        this.defaultFormat = d.isDefault();
        this.updatedAt = d.updatedAt();
    }

    public BoxFormat toDomain() {
        return BoxFormat.rehydrate(id, TenantId.of(tenantId), code, label, spineMm, depthMm, heightMm,
            orientation, quality, maxWeightKg, active, defaultFormat, createdAt, updatedAt);
    }

    public String tenantId() { return tenantId; }
}
