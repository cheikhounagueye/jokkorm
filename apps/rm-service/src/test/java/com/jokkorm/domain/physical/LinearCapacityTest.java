package com.jokkorm.domain.physical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jokkorm.commons.tenancy.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LinearCapacityTest {

    private static final TenantId TENANT = TenantId.of("acme");
    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");

    @Test
    @DisplayName("Tablette de 100 cm, marge 5 % : 95 cm rangeables, soit 9 boîtes dos 10 ou 6 dos 15")
    void tablette_100cm_margin_5() {
        int fillable = LinearCapacity.fillableMm(1000, 5);
        assertThat(fillable).isEqualTo(950);
        assertThat(LinearCapacity.boxesThatFit(fillable, 100)).isEqualTo(9);
        assertThat(LinearCapacity.boxesThatFit(fillable, 150)).isEqualTo(6);
        assertThat(LinearCapacity.boxesThatFit(fillable, 200)).isEqualTo(4);
        assertThat(LinearCapacity.boxesThatFit(fillable, 80)).isEqualTo(11);
    }

    @Test
    @DisplayName("Reste 46 cm : 4 boîtes dos 10 ou 3 boîtes dos 15")
    void remaining_by_format() {
        assertThat(LinearCapacity.boxesThatFit(460, 100)).isEqualTo(4);
        assertThat(LinearCapacity.boxesThatFit(460, 150)).isEqualTo(3);
        assertThat(LinearCapacity.boxesThatFit(-10, 100)).isZero();
    }

    @Test
    @DisplayName("Chaîne de repli : tablette → travée → défaut")
    void fallback_chain() {
        StorageLocation site = StorageLocation.createSite(TENANT, "DKR", null, NOW);
        StorageLocation room = StorageLocation.createChild(site, StorageLocationKind.STOREHOUSE, "S", null, NOW);
        StorageLocation travee = StorageLocation.createChild(room, StorageLocationKind.SHELF, "T01", null, NOW);
        StorageLocation tablette = StorageLocation.createChild(travee, StorageLocationKind.RACK, "1", null, NOW);

        assertThat(LinearCapacity.tabletteLengthMm(tablette, travee, 1000)).isEqualTo(1000);
        travee.changeLength(1200, NOW);
        assertThat(LinearCapacity.tabletteLengthMm(tablette, travee, 1000)).isEqualTo(1200);
        tablette.changeLength(900, NOW);
        assertThat(LinearCapacity.tabletteLengthMm(tablette, travee, 1000)).isEqualTo(900);
    }

    @Test
    @DisplayName("Position migrée de la phase 5S : 6 boîtes × 100 mm = 600 mm, toujours 6 places")
    void migrated_5s_position_keeps_six_places() {
        // La marge s'applique à la tablette, pas à l'emplacement : 600 mm acceptent 6 dos 10.
        assertThat(LinearCapacity.boxesThatFit(600, 100)).isEqualTo(6);
        // La tablette migrée (6 positions × 600 mm) garde sa marge globale.
        assertThat(LinearCapacity.fillableMm(3600, 5)).isEqualTo(3420);
    }

    @Test
    @DisplayName("Affichage en centimètres")
    void cm_display() {
        assertThat(LinearCapacity.cm(180)).isEqualTo("18 cm");
        assertThat(LinearCapacity.cm(45)).isEqualTo("4,5 cm");
        assertThat(LinearCapacity.cm(0)).isEqualTo("0 cm");
    }

    @Test
    @DisplayName("Format de boîte : dimensions bornées, défaut forcément actif")
    void box_format_rules() {
        BoxFormat f = BoxFormat.create(TENANT, "boite-d10", "Boîte dos 10", 100, 250, 330,
            BoxFormat.Quality.CONSERVATION, null, NOW);
        assertThat(f.code()).isEqualTo("BOITE-D10");
        assertThat(f.orientation()).isEqualTo(BoxFormat.Orientation.UPRIGHT);
        f.setDefault(true, NOW);
        assertThatThrownBy(() -> f.setActive(false, NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> BoxFormat.create(TENANT, "X", "x", 5, 250, 330,
            BoxFormat.Quality.STANDARD, null, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Une boîte détruite quitte la tablette")
    void destroyed_box_leaves_shelf() {
        PhysicalContainer c = PhysicalContainer.createEmpty(TENANT, "BOX-1", null, null, null, NOW);
        c.placeAt(java.util.UUID.randomUUID(), NOW);
        c.destroy(NOW);
        assertThat(c.positionId()).isEmpty();
    }
}
