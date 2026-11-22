package com.jokkorm.domain.physical;

/**
 * Cycle de vie d'un lot de provisionnement (phase 5M).
 *
 * <ul>
 *   <li>{@link #CREATED} — lot créé en base, N containers générés, pas encore imprimés.</li>
 *   <li>{@link #PRINTED} — au moins une impression PDF a été générée (firstPrintedAt set).</li>
 *   <li>{@link #FULLY_DEPLOYED} — toutes les boîtes du lot ont été rangées au moins une fois
 *       à une position physique (positionId non null au moins une fois dans leur vie).</li>
 *   <li>{@link #ARCHIVED} — lot clôturé, ne devrait plus apparaître dans les listes actives.
 *       Les boîtes individuelles continuent de vivre leur vie (mouvements, prêts, destruction).</li>
 * </ul>
 */
public enum BatchStatus {
    CREATED,
    PRINTED,
    FULLY_DEPLOYED,
    ARCHIVED
}
