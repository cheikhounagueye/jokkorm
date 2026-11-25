package com.jokkorm.domain.master;

import com.jokkorm.domain.record.Cote;
import java.time.LocalDate;

/**
 * Vue en lecture seule des champs détenus par le maître pour une cote donnée.
 *
 * <p>Lue à la demande. Seuls les champs utiles au regroupement en boîte (service, sort final,
 * échéance, classification, gel) sont recopiés localement, en lecture seule, dans
 * {@link MasterRecordSnapshot} (étude du 24 novembre 2026, D3 ; pattern 03) — jamais le titre.
 *
 * @param cote                identifiant partagé
 * @param title               titre humain du dossier
 * @param classificationPath  chemin dans le plan de classement maître (ex. "finances/marchés/2024")
 * @param duaYears            DUA en années ({@code null} si pas encore décidée)
 * @param finalFate           sort final — VERSEMENT_AN / CONSERVATION / DESTRUCTION ({@code null} si
 *                            pas décidé ou valeur du maître non reconnue : voir {@code finalFateSource})
 * @param legalHold           vrai si gel contentieux actif côté maître
 * @param masterPageUrl       page de l'archive dans l'interface du maître, ouverte par le navigateur
 *                            de l'agent : le maître y applique ses propres droits. JokkoRM n'a
 *                            ainsi pas à copier la description (données personnelles comprises).
 *                            {@code null} si le maître n'a pas de page à ouvrir.
 * @param serviceCode         service producteur chez le maître (Maarch : {@code originatorOrgRegNumber})
 * @param disposalDate        date à laquelle le sort final s'applique ({@code null} si inconnue)
 * @param classificationLevel niveau de classification (confidentialité) ; {@code null} = non classifié
 * @param finalFateSource     sort final tel que le maître l'écrit (Maarch : {@code destruction}…),
 *                            pour nommer une valeur que JokkoRM ne sait pas traduire
 */
public record MasterArchiveView(
    Cote cote,
    String title,
    String classificationPath,
    Integer duaYears,
    String finalFate,
    boolean legalHold,
    String masterPageUrl,
    String serviceCode,
    LocalDate disposalDate,
    String classificationLevel,
    String finalFateSource) {

    public MasterArchiveView {
        if (cote == null) throw new IllegalArgumentException("cote");
    }

    /** Maître sans page à ouvrir ni données de regroupement (connecteurs anciens, tests). */
    public MasterArchiveView(Cote cote, String title, String classificationPath, Integer duaYears,
                             String finalFate, boolean legalHold) {
        this(cote, title, classificationPath, duaYears, finalFate, legalHold, null);
    }

    /** Sans données de regroupement : service, échéance et classification inconnus. */
    public MasterArchiveView(Cote cote, String title, String classificationPath, Integer duaYears,
                             String finalFate, boolean legalHold, String masterPageUrl) {
        this(cote, title, classificationPath, duaYears, finalFate, legalHold, masterPageUrl,
            null, null, null, finalFate);
    }
}
