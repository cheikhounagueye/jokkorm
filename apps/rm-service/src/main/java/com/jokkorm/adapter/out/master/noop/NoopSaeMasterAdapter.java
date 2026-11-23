package com.jokkorm.adapter.out.master.noop;

import com.jokkorm.adapter.out.master.MasterAdapter;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.master.AgentIdentity;
import com.jokkorm.domain.master.DestructionAttestation;
import com.jokkorm.domain.master.MasterArchiveView;
import com.jokkorm.domain.master.MovementEvent;
import com.jokkorm.domain.master.PhysicalLocation;
import com.jokkorm.domain.master.SaeMasterPort;
import com.jokkorm.domain.record.Cote;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Adapter no-op : mode standalone. JokkoRM tourne sans maître ; les notifications
 * sortantes sont loguées en INFO et jetées. Les lectures renvoient {@link Optional#empty()}.
 *
 * <p>Utile pour pilote, démo, ou déploiement captif. Activé via
 * {@code jokkorm.master.kind=noop} (valeur par défaut).
 *
 * <p>Voir ADR-0003 et docs/adapters/README.md.
 */
@Component
public class NoopSaeMasterAdapter implements MasterAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(NoopSaeMasterAdapter.class);

    @Override
    public String kind() { return "noop"; }

    @Override
    public String displayName() { return "Aucun maître (autonome)"; }

    @Override
    public Optional<MasterArchiveView> fetchByCote(TenantId tenant, Cote cote) {
        LOG.debug("[noop] fetchByCote tenant={} cote={} -> empty", tenant.value(), cote.value());
        return Optional.empty();
    }

    @Override
    public Optional<AgentIdentity> resolveAgent(TenantId tenant, String subject) {
        LOG.debug("[noop] resolveAgent tenant={} subject={} -> empty", tenant.value(), subject);
        return Optional.empty();
    }

    @Override
    public void notifyPhysicalLocation(TenantId tenant, Cote cote, PhysicalLocation location) {
        LOG.info("[noop] notifyPhysicalLocation tenant={} cote={} location={}",
            tenant.value(), cote.value(), location.toSingleLine());
    }

    @Override
    public void notifyMovement(TenantId tenant, Cote cote, MovementEvent event) {
        LOG.info("[noop] notifyMovement tenant={} cote={} kind={} agent={} at={}",
            tenant.value(), cote.value(), event.kind(), event.agentSubject(), event.occurredAt());
    }

    @Override
    public void notifyDestructionExecuted(TenantId tenant, Cote cote, DestructionAttestation attestation) {
        LOG.info("[noop] notifyDestructionExecuted tenant={} cote={} attestation={} (pdf {} bytes)",
            tenant.value(), cote.value(), attestation.attestationId(), attestation.signedPdf().length);
    }
}
