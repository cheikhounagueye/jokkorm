package com.jokkorm.commons.web;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.commons.tenancy.TenantResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Résout le tenant actif depuis une requête HTTP. Stratégie :
 *
 * <ol>
 *   <li>Claim JWT {@code tenant} si l'utilisateur est authentifié (source de vérité).</li>
 *   <li>Header {@code X-Tenant-Id} en dernier recours (scénarios admin cross-tenant).</li>
 * </ol>
 *
 * <p>Si rien ne résout, le filter retournera 401 via {@code TenantResolutionException}.
 */
public class HttpTenantResolver implements TenantResolver<HttpServletRequest> {

    public static final String HEADER = "X-Tenant-Id";
    public static final String CLAIM = "tenant";

    @Override
    public Optional<TenantId> resolve(HttpServletRequest request) {
        return fromJwt().or(() -> fromHeader(request));
    }

    private Optional<TenantId> fromJwt() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            String claim = jwt.getClaimAsString(CLAIM);
            if (claim != null && !claim.isBlank()) {
                try {
                    return Optional.of(TenantId.of(claim));
                } catch (IllegalArgumentException invalid) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    private Optional<TenantId> fromHeader(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(TenantId.of(header));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }
}
