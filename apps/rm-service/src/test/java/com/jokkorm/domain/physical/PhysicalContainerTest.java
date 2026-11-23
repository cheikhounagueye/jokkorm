package com.jokkorm.domain.physical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jokkorm.commons.tenancy.TenantId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhysicalContainerTest {

    private static final TenantId TENANT = TenantId.of("acme");
    private static final Instant NOW = Instant.parse("2026-11-22T10:00:00Z");

    @Test
    @DisplayName("createEmpty : barcode normalisé, state EMPTY, pas de position")
    void create_empty() {
        PhysicalContainer c = PhysicalContainer.createEmpty(TENANT, "dmg-2024-112", "Boîte DMG", 50, null, NOW);
        assertThat(c.barcode()).isEqualTo("DMG-2024-112");
        assertThat(c.state()).isEqualTo(ContainerState.EMPTY);
        assertThat(c.positionId()).isEmpty();
        assertThat(c.capacity()).contains(50);
        assertThat(c.lastMovedAt()).isEmpty();
    }

    @Test
    @DisplayName("placeAt : range la boîte et met à jour lastMovedAt")
    void place_at_sets_position_and_timestamp() {
        PhysicalContainer c = PhysicalContainer.createEmpty(TENANT, "DMG-001", null, null, null, NOW);
        UUID positionId = UUID.randomUUID();
        Instant later = NOW.plusSeconds(60);

        c.placeAt(positionId, later);

        assertThat(c.positionId()).contains(positionId);
        assertThat(c.lastMovedAt()).contains(later);
    }

    @Test
    @DisplayName("markHasContent : EMPTY → OPEN")
    void mark_has_content_opens_empty() {
        PhysicalContainer c = PhysicalContainer.createEmpty(TENANT, "DMG-001", null, null, null, NOW);
        c.markHasContent(NOW);
        assertThat(c.state()).isEqualTo(ContainerState.OPEN);
    }

    @Test
    @DisplayName("markHasContent sur SEALED → erreur")
    void sealed_rejects_new_content() {
        PhysicalContainer c = PhysicalContainer.createEmpty(TENANT, "DMG-001", null, null, null, NOW);
        c.markHasContent(NOW);
        c.seal(NOW);

        assertThatThrownBy(() -> c.markHasContent(NOW))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Sealed container");
    }

    @Test
    @DisplayName("destroy est terminal")
    void destroy_is_terminal() {
        PhysicalContainer c = PhysicalContainer.createEmpty(TENANT, "DMG-001", null, null, null, NOW);
        c.destroy(NOW);

        assertThatThrownBy(() -> c.placeAt(UUID.randomUUID(), NOW))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("destroyed");
        assertThatThrownBy(() -> c.seal(NOW))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("barcode rejette blanc / null / mauvais format")
    void barcode_validation() {
        assertThatThrownBy(() -> PhysicalContainer.createEmpty(TENANT, "", null, null, null, NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PhysicalContainer.createEmpty(TENANT, null, null, null, null, NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PhysicalContainer.createEmpty(TENANT, "foo bar", null, null, null, NOW))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("capacity rejette 0 ou négatif")
    void capacity_must_be_positive() {
        assertThatThrownBy(() -> PhysicalContainer.createEmpty(TENANT, "DMG-001", null, 0, null, NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PhysicalContainer.createEmpty(TENANT, "DMG-001", null, -5, null, NOW))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("capacity null = illimité, accepté")
    void capacity_null_means_unlimited() {
        PhysicalContainer c = PhysicalContainer.createEmpty(TENANT, "DMG-001", null, null, null, NOW);
        assertThat(c.capacity()).isEmpty();
    }
}
