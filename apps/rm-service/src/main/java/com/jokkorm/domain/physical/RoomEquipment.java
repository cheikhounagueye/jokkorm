package com.jokkorm.domain.physical;

import com.jokkorm.commons.tenancy.TenantId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Équipement placé dans un magasin (STOREHOUSE). Position en coordonnées normalisées
 * sur le plan de la salle : {@code x} de l'ouest (0) à l'est (1), {@code z} de l'entrée
 * (0) au mur du fond (1). Le rendu 3D convertit ces ratios selon les dimensions qu'il
 * calcule pour la salle, ce qui évite de stocker des mètres que personne n'a mesurés.
 */
public final class RoomEquipment {

    private final UUID id;
    private final TenantId tenantId;
    private final UUID storehouseId;
    private final EquipmentKind kind;
    private final String label;
    private final double x;
    private final double z;
    private LocalDate lastInspectionOn;
    private LocalDate nextInspectionOn;
    private final Instant createdAt;
    private Instant updatedAt;

    private RoomEquipment(
        UUID id, TenantId tenantId, UUID storehouseId, EquipmentKind kind, String label,
        double x, double z, LocalDate lastInspectionOn, LocalDate nextInspectionOn,
        Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.storehouseId = Objects.requireNonNull(storehouseId, "storehouseId");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.label = label == null || label.isBlank() ? null : label.trim();
        this.x = requireRatio(x, "x");
        this.z = requireRatio(z, "z");
        if (lastInspectionOn != null && nextInspectionOn != null && nextInspectionOn.isBefore(lastInspectionOn)) {
            throw new IllegalArgumentException("nextInspectionOn must not be before lastInspectionOn");
        }
        this.lastInspectionOn = lastInspectionOn;
        this.nextInspectionOn = nextInspectionOn;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static RoomEquipment place(
        TenantId tenant, UUID storehouseId, EquipmentKind kind, String label,
        double x, double z, LocalDate lastInspectionOn, LocalDate nextInspectionOn, Instant at) {
        return new RoomEquipment(UUID.randomUUID(), tenant, storehouseId, kind, label,
            x, z, lastInspectionOn, nextInspectionOn, at, at);
    }

    public static RoomEquipment rehydrate(
        UUID id, TenantId tenant, UUID storehouseId, EquipmentKind kind, String label,
        double x, double z, LocalDate lastInspectionOn, LocalDate nextInspectionOn,
        Instant createdAt, Instant updatedAt) {
        return new RoomEquipment(id, tenant, storehouseId, kind, label,
            x, z, lastInspectionOn, nextInspectionOn, createdAt, updatedAt);
    }

    /** Contrôle réglementaire dépassé (la date du prochain contrôle est passée). */
    public boolean inspectionOverdue(LocalDate today) {
        return nextInspectionOn != null && nextInspectionOn.isBefore(today);
    }

    private static double requireRatio(double v, String name) {
        if (Double.isNaN(v) || v < 0.0 || v > 1.0) {
            throw new IllegalArgumentException(name + " must be in [0, 1], got " + v);
        }
        return v;
    }

    public UUID id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public UUID storehouseId() { return storehouseId; }
    public EquipmentKind kind() { return kind; }
    public Optional<String> label() { return Optional.ofNullable(label); }
    public double x() { return x; }
    public double z() { return z; }
    public Optional<LocalDate> lastInspectionOn() { return Optional.ofNullable(lastInspectionOn); }
    public Optional<LocalDate> nextInspectionOn() { return Optional.ofNullable(nextInspectionOn); }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
