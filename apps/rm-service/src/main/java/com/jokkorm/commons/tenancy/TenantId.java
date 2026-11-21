package com.jokkorm.commons.tenancy;

import java.io.Serializable;
import java.util.regex.Pattern;

/**
 * Tenant identifier — a DNS-safe slug used as the multi-tenancy discriminator on every
 * row, every cache key, every storage prefix, and every log line.
 *
 * <p>The slug shape (lowercase alphanumerics and inner hyphens, 1–64 chars) is chosen
 * to remain valid as a Postgres schema name, an OpenSearch index name, an S3 bucket
 * prefix, and a URL path segment without escaping.
 *
 * <p>{@link Serializable} because it ends up in JPA composite PKs: Hibernate refuses to
 * hash a non-serializable PK component.
 *
 * <p>Vendored from eversuite-modern libs/commons-tenancy. Will be extracted to a
 * published Maven artifact {@code jokko-commons} in v1.0.
 */
public record TenantId(String value) implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Pattern SLUG = Pattern.compile("^[a-z0-9]([a-z0-9-]{0,62}[a-z0-9])?$");

    public TenantId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TenantId must not be null or blank");
        }
        if (!SLUG.matcher(value).matches()) {
            throw new IllegalArgumentException(
                "TenantId '%s' is not a valid slug (lowercase alphanumerics and inner hyphens, 1-64 chars)"
                    .formatted(value));
        }
    }

    public static TenantId of(String value) {
        return new TenantId(value);
    }
}
