package com.jokkorm.application.physical;

import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.physical.StorageLocation;
import com.jokkorm.domain.physical.StorageLocationRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Construit l'arbre complet de la topographie pour le tenant courant. Phase 5A :
 * récursion naïve en N+1 (profondeur ≤ 5, nombre de nœuds modéré). Si une topographie
 * dépasse 10000 nœuds en prod, on refacto en recursive CTE SQL — phase ultérieure.
 */
@Service
public class FindStorageLocationTreeUseCase {

    private final StorageLocationRepository repository;

    public FindStorageLocationTreeUseCase(StorageLocationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Node> execute() {
        TenantId tenant = TenantContext.current();
        List<Node> roots = new ArrayList<>();
        for (StorageLocation site : repository.findRoots(tenant)) {
            roots.add(buildNode(tenant, site));
        }
        return roots;
    }

    private Node buildNode(TenantId tenant, StorageLocation location) {
        List<Node> children = new ArrayList<>();
        for (StorageLocation child : repository.findChildren(tenant, location.id())) {
            children.add(buildNode(tenant, child));
        }
        return new Node(location, children);
    }

    public record Node(StorageLocation location, List<Node> children) {}
}
