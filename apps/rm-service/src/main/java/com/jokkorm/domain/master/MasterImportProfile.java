package com.jokkorm.domain.master;

import com.jokkorm.commons.tenancy.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Règle « ce profil a toujours du papier » (règle n° 4) : les nouvelles archives de ce profil
 * entrent seules dans JokkoRM. Décision écrite et datée par le responsable des archives.
 */
public record MasterImportProfile(UUID id, TenantId tenant, String profileReference, boolean autoImport,
                                  String decidedBy, Instant decidedAt) {

    public MasterImportProfile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenant, "tenant");
        Objects.requireNonNull(profileReference, "profileReference");
        Objects.requireNonNull(decidedAt, "decidedAt");
    }
}
