package com.jokkorm.domain.master;

import com.jokkorm.commons.tenancy.TenantId;
import java.util.List;
import java.util.Optional;

/** Règles d'import automatique par profil d'archivage (étape 2). */
public interface MasterImportProfileRepository {

    Optional<MasterImportProfile> find(TenantId tenant, String profileReference);

    List<MasterImportProfile> list(TenantId tenant);

    /** Crée ou remplace la règle du profil (garde le même identifiant). */
    MasterImportProfile save(MasterImportProfile rule);
}
