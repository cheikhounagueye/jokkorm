package com.jokkorm.domain.master;

import com.jokkorm.commons.tenancy.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Archives déposées chez le maître, candidates à l'import (étape 2). */
public interface MasterImportCandidateRepository {

    /** Faux si l'archive du maître a déjà sa ligne (dépôt déjà traité). */
    boolean insertIfAbsent(MasterImportCandidate candidate);

    /** Retire une ligne « illisible » pour pouvoir la réévaluer (droits corrigés, rattrapage). */
    void deleteUnreadable(TenantId tenant, String masterArchiveId);

    Optional<MasterImportCandidate> find(TenantId tenant, UUID id);

    List<MasterImportCandidate> list(TenantId tenant, MasterImportCandidate.Status status, int limit);

    int count(TenantId tenant, MasterImportCandidate.Status status);

    /** Candidates en attente d'un profil, les plus anciennes d'abord. */
    List<MasterImportCandidate> pendingOfProfile(TenantId tenant, String profileReference, int limit);

    /** Profils vus dans les dépôts, avec le nombre en attente. */
    List<ProfileCount> profiles(TenantId tenant);

    record ProfileCount(String profileReference, int pending) {
    }

    /** Change l'état si la ligne est encore dans l'état attendu. Faux sinon (déjà décidée ailleurs). */
    boolean decide(TenantId tenant, UUID id, MasterImportCandidate.Status from, MasterImportCandidate.Status to,
                   String by, Instant at);

    /** Import fait pour cette cote (par la liste ou la recherche) : la candidate en attente passe « importée ». */
    Optional<MasterImportCandidate> markImportedByCote(TenantId tenant, String cote, String by, Instant at);
}
