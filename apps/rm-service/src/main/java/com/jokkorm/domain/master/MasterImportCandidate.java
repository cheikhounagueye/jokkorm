package com.jokkorm.domain.master;

import com.jokkorm.commons.tenancy.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Une archive déposée chez le maître, que JokkoRM ne suit pas encore (étape 2, règle n° 4).
 * Une ligne par archive du maître : c'est aussi le marqueur « dépôt déjà traité ».
 *
 * <p>Seuls l'identifiant chez le maître, la cote, le profil, qui et quand sont gardés. Jamais le
 * titre ni la description : l'écran les lit en direct chez le maître.
 *
 * @param masterArchiveId identifiant interne de l'archive chez le maître
 * @param cote            cote partagée, {@code null} si le maître ne la donne pas (état {@link Status#UNREADABLE})
 * @param masterEventId   événement de dépôt (référence croisée des journaux)
 * @param depositedBy     compte du maître qui a déposé
 * @param auto            vrai si importée automatiquement par une règle de profil
 */
public record MasterImportCandidate(UUID id, TenantId tenant, String masterArchiveId, String cote,
                                    String profileReference, String masterEventId, Instant depositedAt,
                                    String depositedBy, Status status, boolean auto, String decidedBy,
                                    Instant decidedAt, Instant createdAt) {

    public enum Status {
        /** À décider par l'archiviste : importer (il y a du papier) ou ignorer. */
        PENDING,
        /** Entrée dans JokkoRM (à la main ou par une règle de profil). */
        IMPORTED,
        /** Pas de papier : écartée, réversible. */
        IGNORED,
        /** Le maître ne donne pas sa cote au compte de JokkoRM (invisible, sans cote ou supprimée). */
        UNREADABLE
    }

    public MasterImportCandidate {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenant, "tenant");
        Objects.requireNonNull(masterArchiveId, "masterArchiveId");
        Objects.requireNonNull(masterEventId, "masterEventId");
        Objects.requireNonNull(depositedAt, "depositedAt");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static MasterImportCandidate pending(TenantId tenant, MasterEvent ev, String cote, String profile, Instant now) {
        return new MasterImportCandidate(UUID.randomUUID(), tenant, ev.masterArchiveId(), cote, profile, ev.id(),
            ev.occurredAt(), ev.actor(), Status.PENDING, false, null, null, now);
    }

    public static MasterImportCandidate autoImported(TenantId tenant, MasterEvent ev, String cote, String profile,
                                                     String by, Instant now) {
        return new MasterImportCandidate(UUID.randomUUID(), tenant, ev.masterArchiveId(), cote, profile, ev.id(),
            ev.occurredAt(), ev.actor(), Status.IMPORTED, true, by, now, now);
    }

    public static MasterImportCandidate unreadable(TenantId tenant, MasterEvent ev, Instant now) {
        return new MasterImportCandidate(UUID.randomUUID(), tenant, ev.masterArchiveId(), null, null, ev.id(),
            ev.occurredAt(), ev.actor(), Status.UNREADABLE, false, null, null, now);
    }
}
