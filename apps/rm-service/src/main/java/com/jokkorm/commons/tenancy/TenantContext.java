package com.jokkorm.commons.tenancy;

import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Holds the tenant active for the current unit of work.
 *
 * <p>Adapters must wrap their unit of work with {@link #runWith(TenantId, Runnable)}
 * (or {@link #callWith(TenantId, Callable)}). Domain code calls {@link #current()} and
 * treats an unbound state as a bug, not a default.
 *
 * <p><b>Implementation note.</b> Backing storage is a {@link ThreadLocal}. The intended
 * target is {@code java.lang.ScopedValue} (JEP 446 / 481) once it leaves preview on an
 * LTS Java. The public surface mirrors ScopedValue so the migration is a single-class
 * swap with no caller change.
 */
public final class TenantContext {

    private static final ThreadLocal<TenantId> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static boolean isBound() {
        return CURRENT.get() != null;
    }

    public static TenantId current() {
        TenantId value = CURRENT.get();
        if (value == null) {
            throw new IllegalStateException(
                "No tenant bound on the current scope. "
                    + "Call TenantContext.runWith(...) at the request boundary.");
        }
        return value;
    }

    public static TenantId currentOrNull() {
        return CURRENT.get();
    }

    public static void runWith(TenantId tenant, Runnable body) {
        Objects.requireNonNull(tenant, "tenant");
        Objects.requireNonNull(body, "body");
        TenantId previous = CURRENT.get();
        CURRENT.set(tenant);
        try {
            body.run();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static <T> T callWith(TenantId tenant, Callable<T> body) throws Exception {
        Objects.requireNonNull(tenant, "tenant");
        Objects.requireNonNull(body, "body");
        TenantId previous = CURRENT.get();
        CURRENT.set(tenant);
        try {
            return body.call();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    /**
     * Variante qui enveloppe les checked exceptions en {@link RuntimeException}. Utile dans
     * les lambdas de test et les callbacks REST où on ne veut pas propager {@code throws Exception}.
     */
    public static <T> T callWithUnchecked(TenantId tenant, Callable<T> body) {
        try {
            return callWith(tenant, body);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
