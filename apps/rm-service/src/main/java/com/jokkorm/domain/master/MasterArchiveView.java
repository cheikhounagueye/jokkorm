package com.jokkorm.domain.master;

import com.jokkorm.domain.record.Cote;

/**
 * Vue en lecture seule des champs détenus par le maître pour une cote donnée.
 * Jamais stockée côté JokkoRM — récupérée à la demande, cachée courte durée
 * (phase ultérieure, pas en phase 1).
 *
 * @param cote               identifiant partagé
 * @param title              titre humain du dossier
 * @param classificationPath chemin dans le plan de classement maître (ex. "finances/marchés/2024")
 * @param duaYears           DUA en années ({@code null} si pas encore décidée)
 * @param finalFate          sort final — VERSEMENT_AN / CONSERVATION / DESTRUCTION ({@code null} si pas décidé)
 * @param legalHold          vrai si gel contentieux actif côté maître
 * @param masterPageUrl      page de l'archive dans l'interface du maître, ouverte par le navigateur
 *                           de l'agent : le maître y applique ses propres droits. JokkoRM n'a
 *                           ainsi pas à copier la description (données personnelles comprises).
 *                           {@code null} si le maître n'a pas de page à ouvrir.
 */
public record MasterArchiveView(
    Cote cote,
    String title,
    String classificationPath,
    Integer duaYears,
    String finalFate,
    boolean legalHold,
    String masterPageUrl) {

    public MasterArchiveView {
        if (cote == null) throw new IllegalArgumentException("cote");
    }

    /** Maître sans page à ouvrir (démo, connecteurs sans interface). */
    public MasterArchiveView(Cote cote, String title, String classificationPath, Integer duaYears,
                             String finalFate, boolean legalHold) {
        this(cote, title, classificationPath, duaYears, finalFate, legalHold, null);
    }
}
