package com.jokkorm.domain.physical;

import com.jokkorm.commons.tenancy.TenantId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port de persistance pour {@link StorageLocation}. Les implémentations vivent dans
 * {@code adapter/out/persistence/physical/}.
 */
public interface StorageLocationRepository {

    StorageLocation save(StorageLocation location);

    Optional<StorageLocation> findById(TenantId tenant, UUID id);

    Optional<StorageLocation> findByPath(TenantId tenant, String path);

    /** Enfants directs triés par code ascending. */
    List<StorageLocation> findChildren(TenantId tenant, UUID parentId);

    /** Toutes les racines (SITE) du tenant. */
    List<StorageLocation> findRoots(TenantId tenant);

    boolean existsByParentIdAndCode(TenantId tenant, UUID parentId, String code);

    /** Pour SITE (parent null) — unicité du code à la racine. */
    boolean existsRootByCode(TenantId tenant, String code);

    /**
     * Suppression hard. Doit être appelée uniquement après que l'appelant ait vérifié
     * que le nœud est bien vide (pas d'enfants, pas de boîtes). La contrainte FK sur
     * parent_id / container.position_id reste un filet de sécurité DB.
     */
    void delete(TenantId tenant, UUID id);
}
