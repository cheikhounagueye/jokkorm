package com.jokkorm.application;

import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.security.RmFunction;
import java.util.Set;

/**
 * Point d'entrée unique pour appliquer les contrôles d'accès dans les use cases.
 * Dérivé de l'AccessGuard d'eversuite-modern, resserré au périmètre JokkoRM.
 *
 * <p>Deux responsabilités seulement :
 * <ul>
 *   <li>Lier tenant appelant ↔ tenant de la ressource (jamais d'opération cross-tenant).</li>
 *   <li>Vérifier qu'un ensemble de {@link RmFunction} accordé contient la capacité requise.</li>
 * </ul>
 */
public final class AccessGuard {

    private AccessGuard() {}

    /** Lève {@link TenantMismatchException} si le tenant de la ressource diffère du courant. */
    public static void requireSameTenant(TenantId resourceTenant) {
        TenantId current = TenantContext.current();
        if (!current.equals(resourceTenant)) {
            throw new TenantMismatchException(
                "Cross-tenant access blocked: current=%s, resource=%s"
                    .formatted(current.value(), resourceTenant.value()));
        }
    }

    /** Lève {@link AccessDeniedException} si {@code required} n'est pas dans {@code granted}. */
    public static void requireRmFunction(Set<RmFunction> granted, RmFunction required) {
        if (granted == null || !granted.contains(required)) {
            throw new AccessDeniedException("Missing required function: " + required.name());
        }
    }

    public static class AccessDeniedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public AccessDeniedException(String message) { super(message); }
    }

    public static class TenantMismatchException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public TenantMismatchException(String message) { super(message); }
    }
}
