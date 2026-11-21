package com.jokkorm.domain.audit;

import com.jokkorm.commons.tenancy.TenantId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port outbound — append-only. La suppression et la modification sont refusées par
 * trigger SGBD (migration {@code 0003-audit-event.yaml}).
 */
public interface AuditEventRepository {

    AuditEvent save(AuditEvent event);

    /** Dernier évent pour cette cible — sert au calcul de {@code prevEventHash}. */
    Optional<AuditEvent> findLast(TenantId tenant, AuditTargetType targetType, UUID targetId);

    /** Chaîne complète par ordre chronologique — sert à la vérification ex-post. */
    List<AuditEvent> findChain(TenantId tenant, AuditTargetType targetType, UUID targetId);
}
