package com.jokkorm.domain.access;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.common.Page;
import com.jokkorm.domain.common.PageRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccessRequestRepository {
    AccessRequest save(AccessRequest r);
    Optional<AccessRequest> findById(TenantId tenant, UUID id);
    Page<AccessRequest> findAll(TenantId tenant, PageRequest pageRequest);
    /** Demandes du demandeur courant (visibilité REQUESTER). */
    List<AccessRequest> findByRequester(TenantId tenant, String requesterId);
    /** File SUBMITTED à qualifier (archiviste). */
    List<AccessRequest> findSubmitted(TenantId tenant);
    /** File QUALIFIED à approuver (responsable). */
    List<AccessRequest> findQualified(TenantId tenant);
    /** File APPROVED à exécuter (archiviste). */
    List<AccessRequest> findApproved(TenantId tenant);
}
