package com.jokkorm.application.physical;

import com.jokkorm.application.master.MasterSyncService;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.loan.PhysicalLoanRepository;
import com.jokkorm.domain.physical.PhysicalContainer;
import com.jokkorm.domain.physical.PhysicalContainerRepository;
import com.jokkorm.domain.physical.StorageLocation;
import com.jokkorm.domain.physical.StorageLocationRepository;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mode magasin (pattern 12) : « qu'est-ce que je viens de scanner ? ».
 *
 * <p>Une étiquette est soit une boîte (code-barres BOX-…), soit un emplacement (EMP-…). On ne se
 * fie pas au préfixe : le code est cherché dans les deux tables, et un code qui désignerait les
 * deux est refusé plutôt que deviné.
 */
@Service
public class MagasinScanService {

    public record ScannedContainer(PhysicalContainer container, String address, boolean onLoan) {}

    public record ScannedPosition(StorageLocation position, String address) {}

    /** Exactement un des deux est renseigné. */
    public record ScanResult(String code, ScannedContainer container, ScannedPosition position) {}

    private final PhysicalContainerRepository containers;
    private final StorageLocationRepository locations;
    private final PhysicalLoanRepository loans;
    private final MasterSyncService masterSync;
    private final com.jokkorm.domain.record.PhysicalArchiveRecordRepository records;

    public MagasinScanService(
        PhysicalContainerRepository containers,
        StorageLocationRepository locations,
        PhysicalLoanRepository loans,
        MasterSyncService masterSync,
        com.jokkorm.domain.record.PhysicalArchiveRecordRepository records) {
        this.containers = containers;
        this.locations = locations;
        this.loans = loans;
        this.masterSync = masterSync;
        this.records = records;
    }

    @Transactional(readOnly = true)
    public ScanResult resolve(String raw) {
        TenantId tenant = TenantContext.current();
        String code = normalize(raw);
        Optional<PhysicalContainer> box = containers.findByBarcode(tenant, code);
        Optional<StorageLocation> position = locations.findByScanCode(tenant, code);
        if (box.isPresent() && position.isPresent()) {
            throw new PhysicalContainerConflictException(
                "Le code %s désigne à la fois une boîte et un emplacement : étiquette à refaire".formatted(code));
        }
        if (box.isPresent()) return new ScanResult(code, describe(tenant, box.get()), null);
        if (position.isPresent()) {
            return new ScanResult(code, null,
                new ScannedPosition(position.get(), masterSync.address(tenant, position.get())));
        }
        throw new ScanCodeUnknownException("Code inconnu dans cet espace : " + code);
    }

    /** Une boîte trouvée, avec son remplissage (jauge de la règle R4). */
    public record SearchHit(ScannedContainer box, long recordCount) {}

    /**
     * Retrouver une boîte sans l'avoir en main : libellé (contient) ou code-barres (commence par).
     * Sert à la page Étiquettes et au choix d'une boîte existante pour le conditionnement.
     */
    @Transactional(readOnly = true)
    public java.util.List<SearchHit> search(String q, int limit) {
        TenantId tenant = TenantContext.current();
        String query = q == null ? "" : q.trim();
        int max = Math.max(1, Math.min(limit, 50));
        return containers.search(tenant, query, max).stream()
            .map(b -> new SearchHit(describe(tenant, b), records.countByContainer(tenant, b.id())))
            .toList();
    }

    ScannedContainer describe(TenantId tenant, PhysicalContainer box) {
        String address = box.positionId()
            .flatMap(id -> locations.findById(tenant, id))
            .map(p -> masterSync.address(tenant, p))
            .orElse(null);
        // Une simple demande de prêt (REQUESTED) laisse la boîte en rayon.
        boolean onLoan = loans.findActiveByContainer(tenant, box.id())
            .filter(l -> l.status().isActive())
            .isPresent();
        return new ScannedContainer(box, address, onLoan);
    }

    static String normalize(String raw) {
        String code = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (code.isEmpty()) throw new IllegalArgumentException("Code scanné vide");
        return code;
    }
}
