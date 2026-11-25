package com.jokkorm.adapter.out.master;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.master.AgentIdentity;
import com.jokkorm.domain.master.ArchiveSearchCriteria;
import com.jokkorm.domain.master.ArchiveSearchResult;
import com.jokkorm.domain.master.DestructionAttestation;
import com.jokkorm.domain.master.MasterArchiveView;
import com.jokkorm.domain.master.MovementEvent;
import com.jokkorm.domain.master.PhysicalLocation;
import com.jokkorm.domain.master.SaeMasterPort;
import com.jokkorm.domain.record.Cote;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Le {@link SaeMasterPort} injecté partout : il passe chaque appel à l'adapter du tenant.
 * Ainsi le tenant {@code demo} garde son maître de démonstration pendant que le tenant
 * {@code acme} lit le vrai Maarch RM, dans le même déploiement.
 *
 * <p>Démarrage refusé si la configuration désigne un maître absent (ex. {@code maarchrm}
 * sans URL) : mieux vaut un échec au boot qu'un panneau maître vide en démo.
 */
@Component
@Primary
@EnableConfigurationProperties(MasterRoutingProperties.class)
public class RoutingSaeMasterPort implements SaeMasterPort {

    private static final Logger LOG = LoggerFactory.getLogger(RoutingSaeMasterPort.class);

    private final MasterRoutingProperties routing;
    private final Map<String, MasterAdapter> byKind;

    public RoutingSaeMasterPort(List<MasterAdapter> adapters, MasterRoutingProperties routing) {
        this.routing = routing;
        this.byKind = adapters.stream().collect(Collectors.toMap(MasterAdapter::kind, Function.identity()));
        var wanted = new TreeSet<String>();
        wanted.add(routing.kind());
        wanted.addAll(routing.tenants().values());
        for (String kind : wanted) {
            if (!byKind.containsKey(kind)) {
                throw new IllegalStateException(("Maître « %s » demandé par jokkorm.master mais non disponible "
                    + "(disponibles : %s). Pour maarchrm, renseigner JOKKORM_MAARCHRM_BASE_URL.")
                    .formatted(kind, new TreeSet<>(byKind.keySet())));
            }
        }
        LOG.info("Maître par défaut : {} ; par tenant : {}", routing.kind(), routing.tenants());
    }

    /** L'adapter qui sert ce tenant (pour afficher son nom dans l'interface). */
    public MasterAdapter adapterFor(TenantId tenant) {
        return byKind.get(routing.kindFor(tenant.value()));
    }

    @Override
    public Optional<MasterArchiveView> fetchByCote(TenantId tenant, Cote cote) {
        return adapterFor(tenant).fetchByCote(tenant, cote);
    }

    @Override
    public List<ArchiveSearchResult> search(TenantId tenant, ArchiveSearchCriteria criteria) {
        return adapterFor(tenant).search(tenant, criteria);
    }

    @Override
    public Optional<AgentIdentity> resolveAgent(TenantId tenant, String subject) {
        return adapterFor(tenant).resolveAgent(tenant, subject);
    }

    @Override
    public void notifyPhysicalLocation(TenantId tenant, Cote cote, PhysicalLocation location) {
        adapterFor(tenant).notifyPhysicalLocation(tenant, cote, location);
    }

    @Override
    public void notifyMovement(TenantId tenant, Cote cote, MovementEvent event) {
        adapterFor(tenant).notifyMovement(tenant, cote, event);
    }

    @Override
    public void notifyDestructionExecuted(TenantId tenant, Cote cote, DestructionAttestation attestation) {
        adapterFor(tenant).notifyDestructionExecuted(tenant, cote, attestation);
    }

    @Override
    public boolean acceptsPhysicalStatus(TenantId tenant) {
        return adapterFor(tenant).acceptsPhysicalStatus(tenant);
    }

    @Override
    public void publishPhysicalStatus(TenantId tenant, Cote cote, com.jokkorm.domain.master.PhysicalStatus status) {
        adapterFor(tenant).publishPhysicalStatus(tenant, cote, status);
    }

    @Override
    public boolean publishesEvents(TenantId tenant) {
        return adapterFor(tenant).publishesEvents(tenant);
    }

    @Override
    public List<com.jokkorm.domain.master.MasterEvent> readEvents(
        TenantId tenant, java.time.Instant from, java.util.Set<com.jokkorm.domain.master.MasterEvent.Kind> kinds) {
        return adapterFor(tenant).readEvents(tenant, from, kinds);
    }

    @Override
    public Optional<com.jokkorm.domain.master.MasterArchiveRef> describe(TenantId tenant, String masterArchiveId) {
        return adapterFor(tenant).describe(tenant, masterArchiveId);
    }

    @Override
    public Optional<String> contentLinkOrigin(TenantId tenant) {
        return adapterFor(tenant).contentLinkOrigin(tenant);
    }
}
