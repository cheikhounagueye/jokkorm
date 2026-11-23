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
    @DisplayName("Une position sans longueur saisie prend sa part de la tablette (pattern 10)")
    void position_length_is_derived() {
        StorageLocation p = position();
        assertThat(p.lengthMm()).isEmpty();
        assertThat(LinearCapacity.positionLengthMm(p, 1000, 1)).isEqualTo(1000);
        assertThat(LinearCapacity.positionLengthMm(p, 1000, 4)).isEqualTo(250);
    }

    @Test
    @DisplayName("La longueur se fixe en mm dans [50, 30 000], et null revient à la valeur déduite")
    void change_length() {
        StorageLocation p = position();
        p.changeLength(600, NOW);
        assertThat(p.lengthMm()).contains(600);
        p.changeLength(null, NOW);
        assertThat(p.lengthMm()).isEmpty();
        assertThatThrownBy(() -> p.changeLength(49, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> p.changeLength(30_001, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Seules travée, tablette et position ont une longueur de rangement")
    void length_only_on_storage_levels() {
        StorageLocation site = StorageLocation.createSite(TENANT, "DKR", null, NOW);
        StorageLocation room = StorageLocation.createChild(site, StorageLocationKind.STOREHOUSE, "S", null, NOW);
        assertThatThrownBy(() -> site.changeLength(1000, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> room.changeLength(1000, NOW)).isInstanceOf(IllegalArgumentException.class);
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
