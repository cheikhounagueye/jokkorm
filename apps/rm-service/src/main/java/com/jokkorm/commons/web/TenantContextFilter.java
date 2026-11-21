package com.jokkorm.commons.web;

import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.commons.tenancy.TenantResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lie {@link TenantContext} à la portée d'une requête HTTP, puis le libère. Routes
 * exemptées ({@code /actuator/*}, Swagger, …) passent sans tenant.
 *
 * <p>Sans tenant résolu et route non exemptée → {@code 401 Unauthorized} avec code
 * {@code tenant-unresolved}.
 */
public class TenantContextFilter extends OncePerRequestFilter {

    private static final String MDC_KEY = "tenant_id";

    private static final Set<String> EXEMPT_PREFIXES = Set.of(
        "/actuator",
        "/v3/api-docs",
        "/swagger-ui",
        "/favicon.ico"
    );

    private final TenantResolver<HttpServletRequest> resolver;

    public TenantContextFilter(TenantResolver<HttpServletRequest> resolver) {
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain chain) throws ServletException, IOException {

        if (isExempt(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        Optional<TenantId> resolved = resolver.resolve(request);
        if (resolved.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/problem+json");
            response.getWriter().write(
                "{\"type\":\"https://jokkorm.io/errors/tenant-unresolved\","
                    + "\"title\":\"Tenant unresolved\","
                    + "\"status\":401}");
            return;
        }

        TenantId tenant = resolved.get();
        MDC.put(MDC_KEY, tenant.value());
        try {
            TenantContext.runWith(tenant, () -> {
                try {
                    chain.doFilter(request, response);
                } catch (IOException | ServletException e) {
                    throw new RuntimeException(e);
                }
            });
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private boolean isExempt(String uri) {
        for (String prefix : EXEMPT_PREFIXES) {
            if (uri.startsWith(prefix)) return true;
        }
        return false;
    }
}
