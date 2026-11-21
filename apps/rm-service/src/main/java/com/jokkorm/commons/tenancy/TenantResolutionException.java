package com.jokkorm.commons.tenancy;

/**
 * Raised when a {@link TenantResolver} cannot determine the tenant for a request that
 * the caller required to be tenant-bound. Driving adapters typically translate this to
 * an HTTP 400 (or 401 if the caller is unauthenticated).
 */
public class TenantResolutionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TenantResolutionException(String message) {
        super(message);
    }

    public TenantResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
