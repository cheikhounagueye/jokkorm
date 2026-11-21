package com.jokkorm.domain.record;

import java.io.Serializable;
import java.util.regex.Pattern;

/**
 * Cote — identifiant unique du dossier d'archive, émis par le maître (Maarch RM,
 * Alfresco, Nuxeo, …) et repris tel quel par JokkoRM. Voir ADR-0004 et
 * docs/pattern/03-qui-detient-quoi.md.
 *
 * <p>Charset accepté : {@code [A-Z0-9/_-]}, 1–64 caractères. Insensible à la casse :
 * la valeur est normalisée en majuscules à la construction.
 *
 * <p>Pas d'espace, pas d'unicode. Les cotes archivistiques sont ASCII par tradition et
 * doivent rester lisibles sur une étiquette de boîte imprimée.
 */
public record Cote(String value) implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Pattern PATTERN = Pattern.compile("^[A-Z0-9/_\\-]{1,64}$");

    public Cote {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Cote must not be null or blank");
        }
        value = value.toUpperCase();
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                "Cote '%s' invalide : attendu [A-Z0-9/_-]{1,64}".formatted(value));
        }
    }

    public static Cote of(String raw) {
        return new Cote(raw);
    }
}
