package com.jokkorm.domain.master;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.record.Cote;
import java.util.List;
import java.util.Optional;

/**
 * Port unique vers la GED/SAE maître. Voir ADR-0003.
 *
 * <p>Trois adapters livrés (phase 2-3) : {@code noop} (standalone), {@code maarch}
 * (REST Maarch RM), {@code generic} (webhook HMAC configurable). Le kind actif est
 * choisi au boot via la propriété {@code jokkorm.master.kind}.
 *
 * <p>Toutes les méthodes portent {@link TenantId} explicitement : un même déploiement
 * JokkoRM peut adresser N maîtres (un par tenant).
 */
public interface SaeMasterPort {

    // --- Lectures sortantes (JokkoRM demande au maître) ---

    /** Récupère la vérité maître sur une archive par sa cote. {@code empty()} si inconnue. */
    Optional<MasterArchiveView> fetchByCote(TenantId tenant, Cote cote);

    /**
     * Recherche métier (pattern 08). Retourne jusqu'à {@code criteria.limit()} résultats.
     * Par défaut les adapters existants (noop, demo, maarch) renvoient une liste vide
     * si la méthode n'est pas implémentée. Une implémentation réelle filtre côté maître.
     */
    default List<ArchiveSearchResult> search(TenantId tenant, ArchiveSearchCriteria criteria) {
        return List.of();
    }

    /** Résout l'identité d'un agent depuis le claim SSO. Repose sur l'annuaire partagé. */
    Optional<AgentIdentity> resolveAgent(TenantId tenant, String subject);

    // --- Événements sortants (JokkoRM notifie le maître d'un geste physique) ---

    /** Un emplacement vient d'être attribué à la boîte portant cette cote. */
    void notifyPhysicalLocation(TenantId tenant, Cote cote, PhysicalLocation location);

    /** Un mouvement vient d'être tracé (prêt, retour, déplacement, …). */
    void notifyMovement(TenantId tenant, Cote cote, MovementEvent event);

    /** La destruction a été exécutée. On remonte l'attestation PDF scellée. */
    void notifyDestructionExecuted(TenantId tenant, Cote cote, DestructionAttestation attestation);
}
