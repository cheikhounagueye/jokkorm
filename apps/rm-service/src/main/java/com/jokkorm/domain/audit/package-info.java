/**
 * Journal d'audit non-répudiable — chaîne HMAC-SHA256 par tenant et par cible.
 *
 * <p>Voir {@link com.jokkorm.application.audit.AuditChainService} pour le calcul des
 * hashes, et la migration {@code 0003-audit-event.yaml} pour le trigger append-only.
 *
 * <p>Pas de dépendance framework dans ce package.
 */
package com.jokkorm.domain.audit;
