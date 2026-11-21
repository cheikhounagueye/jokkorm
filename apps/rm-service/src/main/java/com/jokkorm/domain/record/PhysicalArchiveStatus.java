package com.jokkorm.domain.record;

/**
 * Statut du cycle de vie physique d'une archive. Cf. docs/pattern/05-cycle-vie-papier.md.
 *
 * <ul>
 *   <li>{@link #REGISTERED} — la cote existe côté JokkoRM, le maître l'a notifiée,
 *       l'enveloppe physique attend le versement effectif.</li>
 *   <li>{@link #STORED} — la boîte est en magasin, un emplacement est attribué.</li>
 *   <li>{@link #FROZEN} — gel contentieux prononcé côté maître, toute sortie bloquée.</li>
 *   <li>{@link #DESTROYED} — destruction exécutée, attestation remontée au maître.</li>
 *   <li>{@link #TRANSFERRED} — versé aux Archives nationales (bordereau SEDA), hors
 *       champ de JokkoRM.</li>
 * </ul>
 */
public enum PhysicalArchiveStatus {
    REGISTERED,
    STORED,
    FROZEN,
    DESTROYED,
    TRANSFERRED
}
