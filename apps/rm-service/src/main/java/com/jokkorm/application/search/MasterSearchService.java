package com.jokkorm.application.search;

import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.master.ArchiveSearchCriteria;
import com.jokkorm.domain.master.ArchiveSearchResult;
import com.jokkorm.domain.master.SaeMasterPort;
import com.jokkorm.domain.physical.PhysicalContainer;
import com.jokkorm.domain.physical.PhysicalContainerRepository;
import com.jokkorm.domain.record.Cote;
import com.jokkorm.domain.record.PhysicalArchiveRecord;
import com.jokkorm.domain.record.PhysicalArchiveRecordRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service de recherche métier (pattern 08). Combine la recherche côté maître
 * avec la résolution cote → container côté JokkoRM.
 *
 * <p>Retourne des {@link EnrichedResult} qui contiennent :
 * <ul>
 *   <li>Les métadonnées métier (titre, service, typologie, dates) venant du maître</li>
 *   <li>L'id du container physique si la cote est rangée chez nous</li>
 *   <li>Le statut d'availabilité physique</li>
 * </ul>
 */
@Service
public class MasterSearchService {

    private static final Logger LOG = LoggerFactory.getLogger(MasterSearchService.class);

    private final SaeMasterPort master;
    private final PhysicalArchiveRecordRepository records;
    private final PhysicalContainerRepository containers;

    public MasterSearchService(
        SaeMasterPort master,
        PhysicalArchiveRecordRepository records,
        PhysicalContainerRepository containers) {
        this.master = master;
        this.records = records;
        this.containers = containers;
    }

    public record EnrichedResult(
        ArchiveSearchResult master,
        UUID containerId,
        String containerBarcode,   // visible côté ARCHIVIST uniquement — à filtrer selon rôle à l'UI
        String containerLabel,
        boolean availableInPhysical) {}

    @Transactional(readOnly = true)
    public List<EnrichedResult> search(ArchiveSearchCriteria criteria) {
        if (!criteria.hasAnyCriteria()) {
            throw new IllegalArgumentException(
                "At least one search criterion is required (free-text, service, typologie, dates, cote)");
        }
        TenantId tenant = TenantContext.current();
        List<ArchiveSearchResult> rawResults = master.search(tenant, criteria);
        LOG.info("Search tenant={} criteria={} → {} master results",
            tenant.value(), criteria, rawResults.size());

        return rawResults.stream()
            .map(r -> enrich(tenant, r))
            .toList();
    }

    private EnrichedResult enrich(TenantId tenant, ArchiveSearchResult master) {
        Optional<PhysicalArchiveRecord> rec = records.findByCote(tenant, Cote.of(master.cote()));
        if (rec.isEmpty() || rec.get().containerId().isEmpty()) {
            return new EnrichedResult(master, null, null, null, false);
        }
        UUID containerId = rec.get().containerId().get();
        PhysicalContainer container = containers.findById(tenant, containerId).orElse(null);
        if (container == null) {
            return new EnrichedResult(master, containerId, null, null, false);
        }
        return new EnrichedResult(master, containerId, container.barcode(),
            container.label(), true);
    }
}
