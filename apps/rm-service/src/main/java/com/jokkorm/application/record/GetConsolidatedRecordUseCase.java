package com.jokkorm.application.record;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.master.MasterArchiveView;
import com.jokkorm.domain.master.SaeMasterPort;
import com.jokkorm.domain.record.Cote;
import com.jokkorm.domain.record.PhysicalArchiveRecord;
import com.jokkorm.domain.record.PhysicalArchiveRecordRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Use case central du pattern « 2 gardiens » (docs/pattern/04-dossier-hybride.md).
 * Récupère l'enveloppe physique locale (JokkoRM est maître) + les champs détenus par la
 * GED/SAE maître (titre, DUA, sort final, gel contentieux) via {@link SaeMasterPort}.
 *
 * <p>Comportement défensif côté maître :
 * <ul>
 *   <li>Si le maître retourne des données → {@link MasterStatus#AVAILABLE}.</li>
 *   <li>Si le maître retourne {@code empty()} (cote inconnue côté maître, ou adapter noop) →
 *       {@link MasterStatus#UNAVAILABLE}, on continue en affichant seulement le physique.</li>
 *   <li>Si le maître jette une exception (réseau / auth / 5xx) → {@link MasterStatus#UNREACHABLE},
 *       loggé en WARN mais non propagé — la vue physique reste utile seule.</li>
 * </ul>
 */
@Service
public class GetConsolidatedRecordUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(GetConsolidatedRecordUseCase.class);

    private final PhysicalArchiveRecordRepository repository;
    private final SaeMasterPort master;

    public GetConsolidatedRecordUseCase(
        PhysicalArchiveRecordRepository repository, SaeMasterPort master) {
        this.repository = repository;
        this.master = master;
    }

    public Result execute(TenantId tenant, Cote cote) {
        PhysicalArchiveRecord local = repository.findByCote(tenant, cote)
            .orElseThrow(() -> new RecordNotFoundException(
                "No physical archive record for cote=%s (tenant=%s)"
                    .formatted(cote.value(), tenant.value())));

        Optional<MasterArchiveView> masterView;
        MasterStatus status;
        try {
            masterView = master.fetchByCote(tenant, cote);
            status = masterView.isPresent() ? MasterStatus.AVAILABLE : MasterStatus.UNAVAILABLE;
        } catch (RuntimeException e) {
            LOG.warn("Master fetchByCote failed for tenant={} cote={} : {}",
                tenant.value(), cote.value(), e.getMessage());
            masterView = Optional.empty();
            status = MasterStatus.UNREACHABLE;
        }

        return new Result(local, masterView.orElse(null), status);
    }

    /**
     * Agrégat retourné : le record local est toujours présent (sinon le use case aurait
     * levé {@link RecordNotFoundException}) ; la vue maître peut être {@code null}.
     */
    public record Result(PhysicalArchiveRecord local, MasterArchiveView master, MasterStatus masterStatus) {}

    public enum MasterStatus {
        /** Le maître a répondu avec des données. */
        AVAILABLE,
        /** Le maître a répondu mais la cote lui est inconnue, ou mode noop. */
        UNAVAILABLE,
        /** Erreur réseau / auth / 5xx côté maître — vue physique utilisable seule. */
        UNREACHABLE
    }
}
