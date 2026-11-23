package com.jokkorm.domain.master;

import com.jokkorm.commons.tenancy.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** File d'envoi vers le maître. Écrite dans la transaction du geste physique. */
public interface MasterSyncRepository {

    /** Demande un (nouvel) envoi pour cette cote : la plus récente demande l'emporte. */
    void touch(TenantId tenant, String cote, Instant at);

    /** Envois dus (à envoyer maintenant), tous tenants confondus. */
    List<MasterSync> findDue(Instant at, int limit);

    Optional<MasterSync> find(TenantId tenant, String cote);

    /** Marque envoyé, seulement si aucune demande n'est arrivée depuis la lecture ({@code version}). */
    boolean markSent(TenantId tenant, String cote, long version, String payload, Instant at);

    /** Panne du maître : nouvel essai à {@code nextAttemptAt}. */
    void markRetry(TenantId tenant, String cote, long version, String error, Instant at, Instant nextAttemptAt);

    /** Refus durable du maître. */
    void markRejected(TenantId tenant, String cote, long version, String error, Instant at);
}
