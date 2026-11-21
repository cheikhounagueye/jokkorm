package com.jokkorm.commons.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TenantContextTest {

    @AfterEach
    void drain() {
        // Defensive: if a test left something bound, don't leak into the next.
        if (TenantContext.isBound()) {
            TenantContext.runWith(TenantContext.current(), () -> {});
        }
    }

    @Test
    @DisplayName("isBound() = false hors scope")
    void is_not_bound_outside_scope() {
        assertThat(TenantContext.isBound()).isFalse();
    }

    @Test
    @DisplayName("current() lève hors scope")
    void current_throws_outside_scope() {
        assertThatThrownBy(TenantContext::current)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No tenant bound");
    }

    @Test
    @DisplayName("inside runWith(), current() renvoie le tenant lié")
    void current_returns_bound_value_inside_scope() {
        TenantId acme = TenantId.of("acme");
        AtomicReference<TenantId> seen = new AtomicReference<>();

        TenantContext.runWith(acme, () -> seen.set(TenantContext.current()));

        assertThat(seen.get()).isEqualTo(acme);
    }

    @Test
    @DisplayName("après runWith(), le binding est relâché")
    void binding_is_released_after_scope() {
        TenantContext.runWith(TenantId.of("acme"), () -> {});
        assertThat(TenantContext.isBound()).isFalse();
    }

    @Test
    @DisplayName("scopes imbriqués : inner masque outer, outer restauré")
    void nested_scopes_shadow_and_restore() {
        TenantId outer = TenantId.of("outer");
        TenantId inner = TenantId.of("inner");
        AtomicReference<TenantId> seenInside = new AtomicReference<>();
        AtomicReference<TenantId> seenAfter = new AtomicReference<>();

        TenantContext.runWith(outer, () -> {
            TenantContext.runWith(inner, () -> seenInside.set(TenantContext.current()));
            seenAfter.set(TenantContext.current());
        });

        assertThat(seenInside.get()).isEqualTo(inner);
        assertThat(seenAfter.get()).isEqualTo(outer);
    }

    @Test
    @DisplayName("runWith() rejette un tenant null")
    void runWith_rejects_null_tenant() {
        assertThatThrownBy(() -> TenantContext.runWith(null, () -> {}))
            .isInstanceOf(NullPointerException.class);
    }
}
