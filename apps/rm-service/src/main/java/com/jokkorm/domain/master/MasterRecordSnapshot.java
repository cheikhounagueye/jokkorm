package com.jokkorm.domain.master;

import com.jokkorm.commons.tenancy.TenantId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Copie locale, en lecture seule, de ce que le maître sait d'un dossier et qui sert à le ranger
 * (étude du 24 novembre 2026, D3 ; pattern 03 : le maître reste le seul propriétaire).
 *
 * <p>Remplie à l'import, rafraîchie par le veilleur et par une relecture périodique. Jamais le
 * titre ni la description. {@code fetchedAt} dit de quand date la copie.
 *
 * @param masterKind          maître qui a répondu (demo, maarchrm…)
 * @param serviceCode         service producteur ; vide = le maître ne le dit pas
 * @param finalFate           sort final traduit (VERSEMENT_AN, CONSERVATION, DESTRUCTION) ou {@code null}
 * @param finalFateSource     sort final tel que le maître l'écrit
 * @param disposalDate        échéance ({@code null} si inconnue)
 * @param classificationLevel niveau de classification ; {@code null} = non classifié
 */
public record MasterRecordSnapshot(
    TenantId tenant,
    UUID recordId,
    String masterKind,
    String serviceCode,
    String finalFate,
    String finalFateSource,
    LocalDate disposalDate,
    String classificationLevel,
    boolean legalHold,
    Instant fetchedAt) {

    public MasterRecordSnapshot {
        Objects.requireNonNull(tenant, "tenant");
        Objects.requireNonNull(recordId, "recordId");
        Objects.requireNonNull(masterKind, "masterKind");
        Objects.requireNonNull(fetchedAt, "fetchedAt");
        serviceCode = blankToNull(serviceCode);
        finalFate = blankToNull(finalFate);
        finalFateSource = blankToNull(finalFateSource);
        classificationLevel = blankToNull(classificationLevel);
    }

    public static MasterRecordSnapshot of(TenantId tenant, UUID recordId, String masterKind,
                                          MasterArchiveView view, Instant fetchedAt) {
        return new MasterRecordSnapshot(tenant, recordId, masterKind, view.serviceCode(), view.finalFate(),
            view.finalFateSource(), view.disposalDate(), view.classificationLevel(), view.legalHold(), fetchedAt);
    }

    public Optional<String> service() { return Optional.ofNullable(serviceCode); }

    public Optional<String> fate() { return Optional.ofNullable(finalFate); }

    public Optional<LocalDate> disposal() { return Optional.ofNullable(disposalDate); }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
