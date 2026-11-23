package com.jokkorm.adapter.out.master;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Quel maître pour quel tenant (ADR-0003 : un maître par tenant).
 *
 * <pre>
 * jokkorm.master.kind=demo                 # maître par défaut
 * jokkorm.master.tenants.acme=maarchrm     # le tenant « acme » parle au vrai Maarch RM
 * </pre>
 *
 * En variables d'environnement : {@code JOKKORM_MASTER_KIND=demo},
 * {@code JOKKORM_MASTER_TENANTS_ACME=maarchrm}.
 *
 * <p>Un même tenant n'a jamais deux maîtres : pas de repli d'un maître sur un autre
 * (pattern 02, règle d'un seul propriétaire).
 */
@ConfigurationProperties(prefix = "jokkorm.master")
public record MasterRoutingProperties(String kind, Map<String, String> tenants) {

    public MasterRoutingProperties {
        kind = kind == null || kind.isBlank() ? "noop" : kind.trim().toLowerCase(Locale.ROOT);
        tenants = tenants == null ? Map.of() : tenants.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(
                e -> e.getKey().trim().toLowerCase(Locale.ROOT),
                e -> e.getValue().trim().toLowerCase(Locale.ROOT)));
    }

    public String kindFor(String tenantId) {
        return tenants.getOrDefault(tenantId, kind);
    }
}
