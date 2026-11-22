package com.jokkorm.domain.master;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Critères de recherche métier côté maître (pattern 08).
 *
 * <p>Au moins un critère doit être rempli — le master adapter est libre de refuser
 * les recherches « donne-moi tout ».
 */
public record ArchiveSearchCriteria(
    String freeText,
    String service,
    String typologie,
    String classificationPath,
    LocalDate dateFrom,
    LocalDate dateTo,
    String cote,
    int limit) {

    public ArchiveSearchCriteria {
        if (limit <= 0 || limit > 100) limit = 20;
    }

    public boolean hasAnyCriteria() {
        return !isBlank(freeText) || !isBlank(service) || !isBlank(typologie)
            || !isBlank(classificationPath) || dateFrom != null || dateTo != null
            || !isBlank(cote);
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    public Optional<String> freeTextOpt() { return Optional.ofNullable(isBlank(freeText) ? null : freeText); }
    public Optional<String> serviceOpt() { return Optional.ofNullable(isBlank(service) ? null : service); }
    public Optional<String> typologieOpt() { return Optional.ofNullable(isBlank(typologie) ? null : typologie); }
    public Optional<String> classificationPathOpt() { return Optional.ofNullable(isBlank(classificationPath) ? null : classificationPath); }
    public Optional<LocalDate> dateFromOpt() { return Optional.ofNullable(dateFrom); }
    public Optional<LocalDate> dateToOpt() { return Optional.ofNullable(dateTo); }
    public Optional<String> coteOpt() { return Optional.ofNullable(isBlank(cote) ? null : cote); }
}
