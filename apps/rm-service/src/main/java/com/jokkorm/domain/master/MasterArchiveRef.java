package com.jokkorm.domain.master;

/**
 * Ce que le veilleur a besoin de savoir d'une archive du maître : sa cote, et l'état physique
 * que le maître affiche en ce moment (le champ écrit par JokkoRM, tranche 2a).
 *
 * @param cote                     cote partagée
 * @param displayedPhysicalStatus  texte affiché par le maître, {@code null} si le champ est absent
 */
public record MasterArchiveRef(String cote, String displayedPhysicalStatus) {
}
