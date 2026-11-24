package com.jokkorm.domain.master;

/**
 * Ce que le veilleur a besoin de savoir d'une archive du maître : sa cote, l'état physique que le
 * maître affiche en ce moment (le champ écrit par JokkoRM, tranche 2a), son profil d'archivage et
 * si c'est une sous-archive (rangée dans un autre dossier). Jamais la description ni le titre.
 *
 * @param cote                     cote partagée
 * @param displayedPhysicalStatus  texte affiché par le maître, {@code null} si le champ est absent
 * @param profileReference         profil d'archivage chez le maître (ex. DOSIP), {@code null} si aucun
 * @param child                    vrai si l'archive est dans une autre archive : elle suit son dossier
 */
public record MasterArchiveRef(String cote, String displayedPhysicalStatus, String profileReference, boolean child) {

    public MasterArchiveRef(String cote, String displayedPhysicalStatus) {
        this(cote, displayedPhysicalStatus, null, false);
    }
}
