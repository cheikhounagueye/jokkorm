package com.jokkorm.adapter.out.master;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jokkorm.adapter.out.master.demo.DemoSaeMasterAdapter;
import com.jokkorm.adapter.out.master.noop.NoopSaeMasterAdapter;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.record.Cote;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RoutingSaeMasterPortTest {

    private final List<MasterAdapter> adapters = List.of(new NoopSaeMasterAdapter(), new DemoSaeMasterAdapter());

    @Test
    @DisplayName("chaque tenant parle à son maître ; les autres prennent le maître par défaut")
    void routes_per_tenant() {
        var routing = new RoutingSaeMasterPort(adapters,
            new MasterRoutingProperties("demo", Map.of("ACME", "noop")));

        assertThat(routing.adapterFor(TenantId.of("demo")).kind()).isEqualTo("demo");
        assertThat(routing.adapterFor(TenantId.of("autre")).kind()).isEqualTo("demo");
        assertThat(routing.adapterFor(TenantId.of("acme")).kind()).isEqualTo("noop");
        assertThat(routing.fetchByCote(TenantId.of("acme"), Cote.of("RH/2020/001"))).isEmpty();
        assertThat(routing.fetchByCote(TenantId.of("demo"), Cote.of("RH/2020/001"))).isPresent();
    }

    @Test
    @DisplayName("maître demandé mais absent : démarrage refusé avec un message clair")
    void unknown_kind_fails_fast() {
        assertThatThrownBy(() -> new RoutingSaeMasterPort(adapters,
            new MasterRoutingProperties("demo", Map.of("acme", "maarchrm"))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("maarchrm")
            .hasMessageContaining("JOKKORM_MAARCHRM_BASE_URL");
    }

    @Test
    @DisplayName("sans configuration : autonome (noop)")
    void defaults_to_noop() {
        var routing = new RoutingSaeMasterPort(adapters, new MasterRoutingProperties(null, null));
        assertThat(routing.adapterFor(TenantId.of("demo")).kind()).isEqualTo("noop");
    }
}
