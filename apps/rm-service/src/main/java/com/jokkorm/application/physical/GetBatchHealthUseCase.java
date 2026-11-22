package com.jokkorm.application.physical;

import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.physical.ContainerBatch;
import com.jokkorm.domain.physical.ContainerBatchRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agrège les stats de santé d'un lot : combien en transit, combien déployées,
 * combien ouvertes, scellées, détruites. Transitionne automatiquement le statut
 * du lot vers FULLY_DEPLOYED si plus aucune boîte n'est en transit.
 */
@Service
public class GetBatchHealthUseCase {

    private final ContainerBatchRepository batches;
    private final Clock clock;

    public GetBatchHealthUseCase(ContainerBatchRepository batches, Clock clock) {
        this.batches = batches;
        this.clock = clock;
    }

    public record Health(
        ContainerBatch batch,
        long total,
        long inTransit,
        long deployed,
        long storedEmpty,
        long open,
        long sealed,
        long destroyed) {

        public int deploymentPercent() {
            if (total == 0) return 0;
            return (int) Math.round((deployed * 100.0) / total);
        }
    }

    @Transactional
    public Health execute(UUID batchId) {
        TenantId tenant = TenantContext.current();
        ContainerBatch batch = batches.findById(tenant, batchId)
            .orElseThrow(() -> new BatchNotFoundException(
                "Batch %s not found (tenant=%s)".formatted(batchId, tenant.value())));

        var counts = batches.countContainersByState(tenant, batchId);
        long deployed = counts.deployed();

        // Transition auto vers FULLY_DEPLOYED si tout est déployé et pas encore marqué
        if (counts.inTransit() == 0
            && batch.status() != com.jokkorm.domain.physical.BatchStatus.FULLY_DEPLOYED
            && batch.status() != com.jokkorm.domain.physical.BatchStatus.ARCHIVED) {
            batch.markFullyDeployed(clock.instant());
            batches.save(batch);
        }

        return new Health(batch, counts.total(),
            counts.inTransit(), deployed,
            counts.storedEmpty(), counts.open(), counts.sealed(), counts.destroyed());
    }
}
