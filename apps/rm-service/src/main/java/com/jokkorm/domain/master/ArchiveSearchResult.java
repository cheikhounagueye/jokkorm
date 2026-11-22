package com.jokkorm.domain.master;

import java.time.LocalDate;

/**
 * Un résultat de recherche maître (pattern 08). Vocabulaire métier — pas de
 * barcode ici, c'est volontaire (confidentialité côté REQUESTER).
 */
public record ArchiveSearchResult(
    String cote,
    String title,
    String service,
    String typologie,
    String classificationPath,
    LocalDate dateStart,
    LocalDate dateEnd,
    Integer duaRemainingYears,
    Boolean communicable) {}
