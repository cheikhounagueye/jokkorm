package com.jokkorm.commons.tenancy;

import java.util.Objects;
import java.util.Optional;

/**
 * Port that converts an inbound request {@code R} into a {@link TenantId}. Concrete
 * adapters (HTTP filter, AMQP listener, batch trigger) implement this interface in
 * their own modules — this type stays free of any framework dependency.
 *
 * @param <R> the request shape the adapter understands
 */
@FunctionalInterface
public interface TenantResolver<R> {

    Optional<TenantId> resolve(R request);

    default TenantId resolveOrThrow(R request) {
        return resolve(request)
            .orElseThrow(() -> new TenantResolutionException("Could not resolve tenant from request"));
    }

    default TenantResolver<R> orElse(TenantResolver<R> fallback) {
        Objects.requireNonNull(fallback, "fallback");
        return req -> {
            Optional<TenantId> primary = this.resolve(req);
            return primary.isPresent() ? primary : fallback.resolve(req);
        };
    }
}
