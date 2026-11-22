package com.jokkorm.domain.access;

import com.jokkorm.commons.tenancy.TenantId;

/**
 * Port vers Maarch RM pour l'upload d'une numérisation (phase 5P).
 *
 * <p>Implémentations :
 * <ul>
 *   <li>{@code StagingMaarchDigitizeAdapter} — stocke le fichier en local
 *       {@code /var/jokkorm/pending-maarch-upload/{tenant}/{uuid}/} + manifest.json,
 *       retourne un faux resId {@code staging:{uuid}}. Mode dev/MVP sans vraie
 *       instance Maarch dispo. Phase 5P par défaut.</li>
 *   <li>{@code MedonaMaarchDigitizeAdapter} — vrai canal M2M Maarch RM via
 *       Medona/SEDA. À implémenter en phase 5P.K dédiée.</li>
 * </ul>
 */
public interface MaarchDigitizePort {

    /**
     * Upload un fichier numérisé associé à une demande d'accès.
     *
     * @param tenant          le tenant JokkoRM propriétaire de la demande
     * @param accessRequestId l'id de la demande (pour traçabilité)
     * @param containerBarcode barcode de la boîte d'origine (contexte métier)
     * @param cote             la cote du dossier concerné (si connue depuis le maître)
     * @param piecesDescription description libre des pièces numérisées
     * @param pdfBytes        le PDF numérisé (fusion des pages scannées)
     * @return le résultat avec resId + URL — peut être réel (Medona) ou staging.
     */
    UploadResult upload(
        TenantId tenant,
        java.util.UUID accessRequestId,
        String containerBarcode,
        String cote,
        String piecesDescription,
        byte[] pdfBytes);

    record UploadResult(String resId, String url, boolean staging) {}
}
