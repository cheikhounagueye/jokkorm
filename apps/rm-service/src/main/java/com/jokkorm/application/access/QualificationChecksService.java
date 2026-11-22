package com.jokkorm.application.access;

import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.access.AccessRequest;
import com.jokkorm.domain.access.AccessRequestRepository;
import com.jokkorm.domain.access.AccessRequestStatus;
import com.jokkorm.domain.access.RequestType;
import com.jokkorm.domain.loan.LoanStatus;
import com.jokkorm.domain.loan.PhysicalLoanRepository;
import com.jokkorm.domain.physical.ContainerState;
import com.jokkorm.domain.physical.PhysicalContainer;
import com.jokkorm.domain.physical.PhysicalContainerRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checks automatiques pour aider la qualification archiviste (phase 5P.E).
 * Appelé avant la transition SUBMITTED → QUALIFIED. L'UI affiche les résultats,
 * l'archiviste décide. Certains checks sont bloquants (DESTROYED, loan actif).
 */
@Service
public class QualificationChecksService {

    private final PhysicalContainerRepository containers;
    private final PhysicalLoanRepository loans;
    private final AccessRequestRepository requests;

    public QualificationChecksService(
        PhysicalContainerRepository containers,
        PhysicalLoanRepository loans,
        AccessRequestRepository requests) {
        this.containers = containers;
        this.loans = loans;
        this.requests = requests;
    }

    @Transactional(readOnly = true)
    public List<QualificationCheck> run(AccessRequest request) {
        TenantId tenant = TenantContext.current();
        List<QualificationCheck> checks = new ArrayList<>();

        PhysicalContainer container = containers.findById(tenant, request.containerId()).orElse(null);
        if (container == null) {
            checks.add(QualificationCheck.block("CONTAINER_EXISTS", "Boîte introuvable"));
            return checks; // inutile de continuer
        }
        checks.add(QualificationCheck.pass("CONTAINER_EXISTS"));

        if (container.state() == ContainerState.DESTROYED) {
            checks.add(QualificationCheck.block("NOT_DESTROYED",
                "Boîte détruite — accès impossible"));
        } else {
            checks.add(QualificationCheck.pass("NOT_DESTROYED"));
        }

        // Loan actif existant (phase 5E héritée)
        loans.findActiveByContainer(tenant, container.id()).ifPresentOrElse(
            l -> checks.add(QualificationCheck.block("NO_ACTIVE_LOAN",
                "Un prêt actif existe déjà pour " + container.barcode()
                    + " (" + l.status() + ", emprunteur " + l.borrowerName() + ")")),
            () -> checks.add(QualificationCheck.pass("NO_ACTIVE_LOAN")));

        // Autre access_request déjà en workflow actif (sauf celle en cours)
        // On évite d'avoir 2 demandes concurrentes BORROW_BOX sur la même boîte
        if (request.type() == RequestType.BORROW_BOX) {
            // Heuristique simple : on vérifie la file APPROVED
            boolean pendingBorrow = requests.findApproved(tenant).stream()
                .anyMatch(r -> !r.id().equals(request.id())
                    && r.containerId().equals(container.id())
                    && r.type() == RequestType.BORROW_BOX);
            if (pendingBorrow) {
                checks.add(QualificationCheck.block("NO_ACTIVE_REQUEST",
                    "Une autre demande BORROW_BOX est déjà approuvée pour cette boîte"));
            } else {
                checks.add(QualificationCheck.pass("NO_ACTIVE_REQUEST"));
            }
        }

        // Zone tampon : warning seulement
        if (container.positionId().isEmpty()) {
            checks.add(QualificationCheck.warn("BUFFER_ZONE",
                "Boîte en zone tampon (jamais rangée). Autorisé mais à signaler."));
        } else {
            checks.add(QualificationCheck.pass("BUFFER_ZONE"));
        }

        // DUA + communicabilité : placeholders — à brancher sur SaeMasterPort en 5P.K
        checks.add(new QualificationCheck("DUA_STATUS",
            QualificationCheck.Severity.INFO, true,
            "Non vérifié (interop Maarch requise)"));
        checks.add(new QualificationCheck("COMMUNICABILITY",
            QualificationCheck.Severity.INFO, true,
            "Non vérifié (interop Maarch requise)"));

        return checks;
    }
}
