package com.jokkorm.domain.physical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jokkorm.commons.tenancy.TenantId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RoomCapacityAndEquipmentTest {

    private static final TenantId TENANT = TenantId.of("acme");
    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");

    private static StorageLocation position() {
        StorageLocation site = StorageLocation.createSite(TENANT, "DKR", null, NOW);
        StorageLocation room = StorageLocation.createChild(site, StorageLocationKind.STOREHOUSE, "SALLE-A", null, NOW);
        StorageLocation shelf = StorageLocation.createChild(room, StorageLocationKind.SHELF, "T01", null, NOW);
        StorageLocation rack = StorageLocation.createChild(shelf, StorageLocationKind.RACK, "1", null, NOW);
        return StorageLocation.createChild(rack, StorageLocationKind.POSITION, "01", null, NOW);
    }

    @Test
    @DisplayName("Une position sans capacité saisie accueille la valeur par défaut")
    void default_capacity() {
        StorageLocation p = position();
        assertThat(p.capacity()).isEmpty();
        assertThat(p.effectiveCapacity()).isEqualTo(StorageLocation.DEFAULT_POSITION_CAPACITY);
    }

    @Test
    @DisplayName("La capacité se fixe sur une position, dans [1, 100], et null revient au défaut")
    void change_capacity() {
        StorageLocation p = position();
        p.changeCapacity(8, NOW);
        assertThat(p.effectiveCapacity()).isEqualTo(8);
        p.changeCapacity(null, NOW);
        assertThat(p.effectiveCapacity()).isEqualTo(StorageLocation.DEFAULT_POSITION_CAPACITY);
        assertThatThrownBy(() -> p.changeCapacity(0, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> p.changeCapacity(101, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Seule une position porte une capacité en boîtes")
    void capacity_only_on_position() {
        StorageLocation site = StorageLocation.createSite(TENANT, "DKR", null, NOW);
        assertThatThrownBy(() -> site.changeCapacity(5, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(site::effectiveCapacity).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Équipement : coordonnées normalisées et contrôle en retard")
    void equipment_rules() {
        UUID room = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 11, 23);
        RoomEquipment late = RoomEquipment.place(TENANT, room, EquipmentKind.FIRE_EXTINGUISHER, "E4",
            1.0, 0.8, today.minusYears(1), today.minusDays(1), NOW);
        RoomEquipment fine = RoomEquipment.place(TENANT, room, EquipmentKind.FIRE_EXTINGUISHER, "E1",
            0.0, 0.2, today.minusMonths(6), today.plusMonths(6), NOW);
        assertThat(late.inspectionOverdue(today)).isTrue();
        assertThat(fine.inspectionOverdue(today)).isFalse();
        assertThat(EquipmentKind.SMOKE_DETECTOR.ceilingMounted()).isTrue();
        assertThatThrownBy(() -> RoomEquipment.place(TENANT, room, EquipmentKind.EMERGENCY_EXIT, null,
            1.2, 0.0, null, null, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RoomEquipment.place(TENANT, room, EquipmentKind.FIRE_EXTINGUISHER, null,
            0.0, 0.0, today, today.minusDays(1), NOW)).isInstanceOf(IllegalArgumentException.class);
    }
}
