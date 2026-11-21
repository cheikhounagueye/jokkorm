/**
 * Bounded context <em>record</em> — l'enveloppe physique d'une archive. Agrégat racine
 * {@link com.jokkorm.domain.record.PhysicalArchiveRecord}, identifiant partagé
 * {@link com.jokkorm.domain.record.Cote} (émis par le maître, repris tel quel).
 *
 * <p>Pas de dépendance framework ici. Voir ADR-0001 et ADR-0004.
 */
package com.jokkorm.domain.record;
