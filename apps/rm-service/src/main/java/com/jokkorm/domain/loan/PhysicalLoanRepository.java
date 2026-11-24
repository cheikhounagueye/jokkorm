package com.jokkorm.domain.loan;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.common.Page;
import com.jokkorm.domain.common.PageRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PhysicalLoanRepository {

    PhysicalLoan save(PhysicalLoan loan);

    Optional<PhysicalLoan> findById(TenantId tenant, UUID id);

    /** Le prêt dont le fantôme porte ce code ({@code PRT-…}), quel que soit son état. */
    Optional<PhysicalLoan> findByGhostCode(TenantId tenant, String ghostCode);

    /** Prêt actif (REQUESTED/OUT/OVERDUE) pour un container donné, s'il existe. */
    Optional<PhysicalLoan> findActiveByContainer(TenantId tenant, UUID containerId);

    Page<PhysicalLoan> findAll(TenantId tenant, PageRequest pageRequest);

    /** Prêts actuellement OUT ou en retard — pour le dashboard "boîtes dehors". */
    List<PhysicalLoan> findActive(TenantId tenant);
}
