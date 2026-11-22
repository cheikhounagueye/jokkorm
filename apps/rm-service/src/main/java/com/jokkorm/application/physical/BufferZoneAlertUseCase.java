package com.jokkorm.application.physical;

import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.physical.PhysicalContainerRepository;
import com.jokkorm.domain.physical.PhysicalContainerRepository.StaleBufferEntry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 5O — alertes zone tampon.
 *
 * <p>Rappelle à l'archiviste qu'il y a des boîtes provisionnées mais jamais rangées,
 * depuis plus de {@code jokkorm.physical.buffer-zone-alert-days} jours (défaut 14).
 * Pattern 06 : zone tampon = positionId=NULL, on évite d'y "oublier" des boîtes.
 *
 * <p>Pas de job scheduled — l'UI appelle cet endpoint à chaque chargement de
 * /magasin/lots. Calcul SQL pur, pas de job batch.
 */
@Service
public class BufferZoneAlertUseCase {

    private final PhysicalContainerRepository containers;
    private final Clock clock;
    private final long alertDays;

    public BufferZoneAlertUseCase(
        PhysicalContainerRepository containers,
        Clock clock,
        @Value("${jokkorm.physical.buffer-zone-alert-days:14}") long alertDays) {
        this.containers = containers;
        this.clock = clock;
        this.alertDays = Math.max(0, alertDays); // 0 = alerte immédiate (test/démo)
    }

    public record Alert(
        /** Nombre de jours configurés pour déclencher l'alerte. */
        long thresholdDays,
        /** Total boîtes stale toutes lots confondus. */
        long totalStaleContainers,
        /** Détail par lot (lots sans stale exclus). */
        List<StaleBufferEntry> byBatch) {

        public boolean hasAlerts() { return totalStaleContainers > 0; }
    }

    @Transactional(readOnly = true)
    public Alert execute() {
        TenantId tenant = TenantContext.current();
        Instant threshold = clock.instant().minus(Duration.ofDays(alertDays));
        List<StaleBufferEntry> entries = containers.findStaleBufferByBatch(tenant, threshold);
        long total = entries.stream().mapToLong(StaleBufferEntry::staleCount).sum();
        return new Alert(alertDays, total, entries);
    }
}
