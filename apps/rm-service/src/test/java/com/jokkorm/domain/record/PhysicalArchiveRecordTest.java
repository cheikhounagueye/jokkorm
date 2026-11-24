package com.jokkorm.domain.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jokkorm.commons.tenancy.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhysicalArchiveRecordTest {

    private static final TenantId TENANT = TenantId.of("demo");
    private static final Cote COTE = Cote.of("DMG/MP/2024/047");
    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");

    @Test
    @DisplayName("register() crée un record REGISTERED avec un UUID")
    void register_creates_registered_record() {
        PhysicalArchiveRecord r = PhysicalArchiveRecord.register(TENANT, COTE, NOW);

        assertThat(r.id()).isNotNull();
        assertThat(r.tenantId()).isEqualTo(TENANT);
        assertThat(r.cote()).isEqualTo(COTE);
        assertThat(r.status()).isEqualTo(PhysicalArchiveStatus.REGISTERED);
        assertThat(r.registeredAt()).isEqualTo(NOW);
        assertThat(r.lastTransitionAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("markStored() : REGISTERED → STORED")
    void mark_stored_from_registered() {
        PhysicalArchiveRecord r = PhysicalArchiveRecord.register(TENANT, COTE, NOW);
        Instant later = NOW.plusSeconds(60);

        r.markStored(later);

        assertThat(r.status()).isEqualTo(PhysicalArchiveStatus.STORED);
        assertThat(r.lastTransitionAt()).isEqualTo(later);
    }

    @Test
    @DisplayName("markFrozen() bloque la destruction")
    void frozen_blocks_destruction() {
        PhysicalArchiveRecord r = PhysicalArchiveRecord.register(TENANT, COTE, NOW);
        r.markStored(NOW);
        r.markFrozen(NOW);

        assertThatThrownBy(() -> r.markDestroyed(NOW))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("frozen");
    }

    @Test
    @DisplayName("releaseFreeze() ramène FROZEN → STORED")
    void release_freeze_returns_to_stored() {
        PhysicalArchiveRecord r = PhysicalArchiveRecord.register(TENANT, COTE, NOW);
        r.markStored(NOW);
        r.markFrozen(NOW);

        r.releaseFreeze(NOW.plusSeconds(100));

        assertThat(r.status()).isEqualTo(PhysicalArchiveStatus.STORED);
    }

    @Test
    @DisplayName("markDestroyed() est terminal — toute transition ultérieure rejetée")
    void destroyed_is_terminal() {
        PhysicalArchiveRecord r = PhysicalArchiveRecord.register(TENANT, COTE, NOW);
        r.markStored(NOW);
        r.markDestroyed(NOW);

        assertThatThrownBy(() -> r.markStored(NOW))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already destroyed");
    }

    @Test
    @DisplayName("markTransferred() est terminal (versement AN)")
    void transferred_is_terminal() {
        PhysicalArchiveRecord r = PhysicalArchiveRecord.register(TENANT, COTE, NOW);
        r.markStored(NOW);
        r.markTransferred(NOW);

        assertThatThrownBy(() -> r.markDestroyed(NOW))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("markFrozen() interdit si TRANSFERRED")
    void cannot_freeze_transferred() {
        PhysicalArchiveRecord r = PhysicalArchiveRecord.register(TENANT, COTE, NOW);
        r.markStored(NOW);
        r.markTransferred(NOW);

        assertThatThrownBy(() -> r.markFrozen(NOW))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("releaseFreeze() interdit si non FROZEN")
    void cannot_release_if_not_frozen() {
        PhysicalArchiveRecord r = PhysicalArchiveRecord.register(TENANT, COTE, NOW);
        r.markStored(NOW);

        assertThatThrownBy(() -> r.releaseFreeze(NOW))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Un dossier, une seule boîte : ranger dans une 2ᵉ boîte est refusé, la même boîte ne change rien")
    void store_never_switches_box_silently() {
        java.util.UUID a = java.util.UUID.randomUUID();
        java.util.UUID b = java.util.UUID.randomUUID();
        PhysicalArchiveRecord r = PhysicalArchiveRecord.register(TENANT, COTE, NOW);
        r.storeInContainer(a, NOW);

        assertThatThrownBy(() -> r.storeInContainer(b, NOW.plusSeconds(60)))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("transfert");
        assertThat(r.containerId()).contains(a);

        r.storeInContainer(a, NOW.plusSeconds(120));
        assertThat(r.lastTransitionAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Transfert : explicite, depuis une boîte, vers une autre boîte, jamais sur un dossier gelé")
    void transfer_rules() {
        java.util.UUID a = java.util.UUID.randomUUID();
        java.util.UUID b = java.util.UUID.randomUUID();
        PhysicalArchiveRecord r = PhysicalArchiveRecord.register(TENANT, COTE, NOW);

        assertThatThrownBy(() -> r.transferToContainer(b, NOW)).isInstanceOf(IllegalStateException.class);
        r.storeInContainer(a, NOW);
        assertThatThrownBy(() -> r.transferToContainer(a, NOW)).isInstanceOf(IllegalStateException.class);

        r.transferToContainer(b, NOW.plusSeconds(60));
        assertThat(r.containerId()).contains(b);
        assertThat(r.status()).isEqualTo(PhysicalArchiveStatus.STORED);

        r.markFrozen(NOW.plusSeconds(120));
        assertThatThrownBy(() -> r.transferToContainer(a, NOW.plusSeconds(180)))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("register() rejette tenant/cote/at null")
    void register_rejects_nulls() {
        assertThatThrownBy(() -> PhysicalArchiveRecord.register(null, COTE, NOW))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PhysicalArchiveRecord.register(TENANT, null, NOW))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PhysicalArchiveRecord.register(TENANT, COTE, null))
            .isInstanceOf(NullPointerException.class);
    }
}
