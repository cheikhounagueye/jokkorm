package com.jokkorm.commons.role;

import java.util.Set;

/**
 * ThreadLocal pour le set de rôles de l'utilisateur courant (phase 5P).
 * Rempli par {@link RoleFilter} à partir du header {@code X-Role} en dev,
 * puis par un OIDC filter en phase 4A sans toucher au reste.
 */
public final class RoleContext {

    private static final ThreadLocal<Set<UserRole>> ROLES = new ThreadLocal<>();

    private RoleContext() {}

    public static void set(Set<UserRole> roles) {
        ROLES.set(roles == null ? Set.of() : Set.copyOf(roles));
    }

    public static Set<UserRole> current() {
        Set<UserRole> r = ROLES.get();
        return r == null ? Set.of() : r;
    }

    public static boolean has(UserRole role) {
        return current().contains(role);
    }

    public static boolean hasAny(UserRole... roles) {
        var current = current();
        for (UserRole r : roles) if (current.contains(r)) return true;
        return false;
    }

    public static void requireAny(UserRole... roles) {
        if (!hasAny(roles)) {
            throw new ForbiddenRoleException(
                "Required one of: " + java.util.Arrays.toString(roles)
                    + " (current=" + current() + ")");
        }
    }

    public static void clear() { ROLES.remove(); }
}
