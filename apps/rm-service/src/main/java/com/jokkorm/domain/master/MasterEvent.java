package com.jokkorm.domain.master;

import java.time.Instant;
import java.util.Objects;

/**
 * Un événement lu dans le journal du maître, réduit au strict nécessaire : type, archive
 * (identifiant interne du maître), qui, quand. Jamais le contenu de l'événement (description,
 * valeurs saisies) : il peut porter des données personnelles.
 *
 * @param id              identifiant de l'événement chez le maître (référence croisée des journaux)
 * @param kind            type d'événement
 * @param occurredAt      horodatage donné par le maître
 * @param masterArchiveId identifiant interne de l'archive chez le maître (pas la cote)
 * @param actor           compte du maître auteur de l'événement
 * @param byConnector     vrai si l'auteur est le compte de connexion de JokkoRM lui-même
 */
public record MasterEvent(String id, Kind kind, Instant occurredAt, String masterArchiveId, String actor,
                          boolean byConnector) {

    public enum Kind {
        /** Métadonnées de l'archive modifiées (description, nom…). */
        METADATA_MODIFIED,
        /** Gel contentieux posé. */
        FROZEN,
        /** Gel contentieux levé. */
        UNFROZEN
    }

    public MasterEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(masterArchiveId, "masterArchiveId");
    }
}
