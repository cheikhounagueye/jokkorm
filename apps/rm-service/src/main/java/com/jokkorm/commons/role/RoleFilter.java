package com.jokkorm.commons.role;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Dev-mode : extrait les rôles du header {@code X-Role} (CSV ex. "REQUESTER,ARCHIVIST").
 * Vide par défaut → l'utilisateur n'a aucun rôle (REQUESTER-like depuis header séparé).
 * Remplacé en phase 4A par un vrai OIDC filter sans toucher au code applicatif.
 */
@Component
@Order(2) // après TenantFilter mais avant les contrôleurs
public class RoleFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("X-Role");
        Set<UserRole> roles = parseRoles(header);
        try {
            RoleContext.set(roles);
            chain.doFilter(request, response);
        } finally {
            RoleContext.clear();
        }
    }

    private Set<UserRole> parseRoles(String header) {
        if (header == null || header.isBlank()) return EnumSet.noneOf(UserRole.class);
        return Arrays.stream(header.split(","))
            .map(String::trim).filter(s -> !s.isEmpty())
            .map(s -> {
                try { return UserRole.valueOf(s.toUpperCase()); }
                catch (IllegalArgumentException e) { return null; }
            })
            .filter(r -> r != null)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(UserRole.class)));
    }
}
