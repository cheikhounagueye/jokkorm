package com.jokkorm.domain.physical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jokkorm.commons.tenancy.TenantId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Mode magasin (pattern 12) : code d'étiquette des emplacements, arrivée et retrait d'une boîte. */
class MagasinDomainTest {

    private static final TenantId TENANT = TenantId.of("demo");
    private static final Instant T0 = Instant.parse("2026-11-24T10:00:00Z");

    @Test
    @DisplayName("Seul un emplacement reçoit un code d'étiquette EMP-XXXXXXXX, différent à chaque création")
    void only_positions_get_a_scan_code() {
        StorageLocation site = StorageLocation.createSite(TENANT, "DKR", null, T0);
        StorageLocation room = StorageLocation.createChild(site, StorageLocationKind.STOREHOUSE, "SALLE", null, T0);
        StorageLocation shelf = StorageLocation.createChild(room, StorageLocationKind.SHELF, "T01", null, T0);
        StorageLocation rack = StorageLocation.createChild(shelf, StorageLocationKind.RACK, "1", null, T0);
        StorageLocation a = StorageLocation.createChild(rack, StorageLocationKind.POSITION, "01", null, T0);
        StorageLocation b = StorageLocation.createChild(rack, StorageLocationKind.POSITION, "02", null, T0);

        assertThat(site.scanCode()).isEmpty();
        assertThat(room.scanCode()).isEmpty();
        assertThat(shelf.scanCode()).isEmpty();
        assertThat(rack.scanCode()).isEmpty();
        assertThat(a.scanCode()).get().asString().matches("^EMP-[0-9A-HJKMNP-TV-Z]{8}$");
        assertThat(b.scanCode()).isNotEqualTo(a.scanCode());
    }

    @Test
    @DisplayName("Relire un emplacement sans code, ou un autre niveau avec un code, est refusé")
    void scan_code_shape_is_enforced_on_rehydrate() {
        UUID parent = UUID.randomUUID();
        assertThatThrownBy(() -> StorageLocation.rehydrate(UUID.randomUUID(), TENANT, StorageLocationKind.POSITION,
            "01", null, parent, "DKR/SALLE/T01/1/01", null, PhysicalSpec.EMPTY, null, T0, T0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StorageLocation.rehydrate(UUID.randomUUID(), TENANT, StorageLocationKind.RACK,
            "1", null, parent, "DKR/SALLE/T01/1", null, PhysicalSpec.EMPTY, "EMP-0000AAAA", T0, T0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Arrivée : pointée une fois ; re-pointer sans mouvement ne change rien ; après un retrait, nouvelle date")
    void receive_is_idempotent_until_the_box_moves() {
        PhysicalContainer box = PhysicalContainer.createEmpty(TENANT, "BOX-261124-AAAAAA", "Test", null, null, T0);

        assertThat(box.receive("awa", T0.plusSeconds(60))).isTrue();
        assertThat(box.receivedBy()).contains("awa");
        assertThat(box.receive("moussa", T0.plusSeconds(120))).isFalse();
        assertThat(box.receivedAt()).contains(T0.plusSeconds(60));
        assertThat(box.receivedBy()).contains("awa");

        box.placeAt(UUID.randomUUID(), T0.plusSeconds(180));
        assertThatThrownBy(() -> box.receive("awa", T0.plusSeconds(200))).isInstanceOf(IllegalStateException.class);

        box.removeFromShelf(T0.plusSeconds(240));
        assertThat(box.receive("moussa", T0.plusSeconds(300))).isTrue();
        assertThat(box.receivedAt()).contains(T0.plusSeconds(300));
        assertThat(box.receivedBy()).contains("moussa");
    }

    @Test
    @DisplayName("Retrait : la boîte quitte sa position ; impossible si elle n'est sur aucune tablette ou détruite")
    void remove_from_shelf() {
        PhysicalContainer box = PhysicalContainer.createEmpty(TENANT, "BOX-261124-BBBBBB", "Test", null, null, T0);
        assertThatThrownBy(() -> box.removeFromShelf(T0)).isInstanceOf(IllegalStateException.class);

        UUID position = UUID.randomUUID();
        box.placeAt(position, T0.plusSeconds(10));
        assertThat(box.removeFromShelf(T0.plusSeconds(20))).isEqualTo(position);
        assertThat(box.positionId()).isEmpty();
        assertThat(box.lastMovedAt()).contains(T0.plusSeconds(20));

        box.placeAt(position, T0.plusSeconds(30));
        box.destroy(T0.plusSeconds(40));
        assertThatThrownBy(() -> box.removeFromShelf(T0.plusSeconds(50))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> box.receive("awa", T0.plusSeconds(50))).isInstanceOf(IllegalStateException.class);
    }
}
