package com.jokkorm.adapter.in.web.dev;

import com.jokkorm.commons.tenancy.TenantId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Espaces (tenants) de démonstration : lister et créer, pour faire cohabiter le tenant
 * {@code demo} (maître de démo) et un tenant branché sur un vrai Maarch RM.
 * Dev uniquement ({@code JOKKORM_DEV_SEEDING_ENABLED=true}) : la liste des tenants n'est
 * jamais exposée en production.
 */
@RestController
@RequestMapping("/api/v1/dev/tenants")
@ConditionalOnProperty(name = "jokkorm.dev.seeding-enabled", havingValue = "true")
@Tag(name = "Dev", description = "Données de démonstration. Jamais en prod.")
public class DevTenantController {

    private final JdbcTemplate jdbc;

    public DevTenantController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record TenantView(String id, String displayName) {}

    public record CreateTenant(@NotBlank String id, @NotBlank String displayName) {}

    @GetMapping
    @Operation(summary = "Liste des espaces (dev).")
    public List<TenantView> list() {
        return jdbc.query("SELECT id, display_name FROM tenant ORDER BY id",
            (rs, i) -> new TenantView(rs.getString(1), rs.getString(2)));
    }

    @PostMapping
    @Operation(summary = "Crée un espace s'il n'existe pas (dev, idempotent).")
    public TenantView create(@Valid @RequestBody CreateTenant request) {
        String id = TenantId.of(request.id().trim()).value();
        jdbc.update("INSERT INTO tenant (id, display_name) VALUES (?, ?) ON CONFLICT (id) DO NOTHING",
            id, request.displayName().trim());
        return jdbc.queryForObject("SELECT id, display_name FROM tenant WHERE id = ?",
            (rs, i) -> new TenantView(rs.getString(1), rs.getString(2)), id);
    }
}
