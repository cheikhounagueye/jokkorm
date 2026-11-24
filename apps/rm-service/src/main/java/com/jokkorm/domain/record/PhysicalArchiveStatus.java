package com.jokkorm.domain.record;

/**
 * Statut du cycle de vie physique d'une archive. Cf. docs/pattern/05-cycle-vie-papier.md.
 *
 * <ul>
 *   <li>{@link #REGISTERED} — la cote existe côté JokkoRM, le maître l'a notifiée,
 *       l'enveloppe physique attend le versement effectif.</li>
 *   <li>{@link #STORED} — le dossier est dans une boîte. La boîte peut être encore en zone tampon
 *       (conditionnement avant mise en rayon, étude du 24 novembre 2026, D1) : l'adresse se lit
 *       alors sur la boîte, jamais sur le dossier.</li>
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
