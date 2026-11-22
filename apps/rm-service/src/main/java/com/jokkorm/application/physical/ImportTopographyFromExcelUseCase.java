package com.jokkorm.application.physical;

import com.jokkorm.application.audit.AuditChainService;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.audit.AuditAction;
import com.jokkorm.domain.audit.AuditTargetType;
import com.jokkorm.domain.physical.StorageLocation;
import com.jokkorm.domain.physical.StorageLocationKind;
import com.jokkorm.domain.physical.StorageLocationRepository;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 5I — importe un fichier Excel de topographie en 1 opération atomique.
 *
 * <p>Format du fichier (voir {@link TopographyExcelTemplateGenerator}) — 1 ligne par nœud :
 * <pre>
 *   | kind      | code      | label               | parent_path                |
 *   | SITE      | DKR       | Dakar siège         |                            |
 *   | STOREHOUSE| MAG-01    | Magasin principal   | DKR                        |
 *   | SHELF     | A         | Travée A            | DKR/MAG-01                 |
 *   | RACK      | 1         | Tablette 1          | DKR/MAG-01/A               |
 *   | POSITION  | 001       | Position 001        | DKR/MAG-01/A/1             |
 * </pre>
 *
 * <p>parent_path est le chemin des codes des ancêtres séparés par /. Vide pour un SITE.
 * L'ordre importe : un enfant ne peut pas être importé avant son parent (même fichier).
 * Si un code existe déjà à cet emplacement, il est ignoré (idempotent pour rejouer).
 */
@Service
public class ImportTopographyFromExcelUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(ImportTopographyFromExcelUseCase.class);

    private final StorageLocationRepository locations;
    private final AuditChainService audit;
    private final Clock clock;

    public ImportTopographyFromExcelUseCase(
        StorageLocationRepository locations,
        AuditChainService audit,
        Clock clock) {
        this.locations = locations;
        this.audit = audit;
        this.clock = clock;
    }

    public record ImportReport(
        int created,
        int skippedExisting,
        List<String> errors) {}

    @Transactional
    public ImportReport execute(InputStream excelStream, String actor) {
        TenantId tenant = TenantContext.current();
        int created = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        // Cache des nœuds déjà résolus pendant l'import : path "DKR/MAG-01" → StorageLocation
        Map<String, StorageLocation> pathToNode = new HashMap<>();
        // Pré-remplir avec les racines déjà en base pour pouvoir attacher des enfants à des SITEs existants
        for (StorageLocation root : locations.findRoots(tenant)) {
            pathToNode.put(root.code(), root);
            indexDescendants(tenant, root, root.code(), pathToNode);
        }

        try (Workbook workbook = new XSSFWorkbook(excelStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();
            for (int r = 1; r <= lastRow; r++) { // r=0 = en-tête
                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row)) continue;
                try {
                    String kindStr = cellString(row, 0);
                    String code = cellString(row, 1);
                    String label = cellString(row, 2);
                    String parentPath = cellString(row, 3);

                    if (kindStr.isBlank() || code.isBlank()) {
                        errors.add("Ligne %d : kind et code obligatoires".formatted(r + 1));
                        continue;
                    }

                    StorageLocationKind kind = parseKind(kindStr, r + 1, errors);
                    if (kind == null) continue;

                    String fullPath = parentPath.isBlank() ? code : parentPath + "/" + code;
                    // Idempotence : si ce chemin exact existe déjà, on skip
                    if (pathToNode.containsKey(fullPath)) {
                        skipped++;
                        continue;
                    }

                    StorageLocation toCreate;
                    if (parentPath.isBlank()) {
                        if (kind != StorageLocationKind.SITE) {
                            errors.add("Ligne %d : un nœud sans parent_path doit être SITE (reçu %s)"
                                .formatted(r + 1, kind));
                            continue;
                        }
                        toCreate = StorageLocation.createSite(
                            tenant, code, label.isBlank() ? null : label, clock.instant());
                    } else {
                        StorageLocation parent = pathToNode.get(parentPath);
                        if (parent == null) {
                            errors.add("Ligne %d : parent_path '%s' introuvable (ni importé plus haut ni déjà en base)"
                                .formatted(r + 1, parentPath));
                            continue;
                        }
                        toCreate = StorageLocation.createChild(
                            parent, kind, code, label.isBlank() ? null : label, clock.instant());
                    }

                    StorageLocation saved = locations.save(toCreate);
                    pathToNode.put(fullPath, saved);
                    created++;
                } catch (RuntimeException e) {
                    errors.add("Ligne %d : %s".formatted(r + 1, e.getMessage()));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read Excel file: " + e.getMessage(), e);
        }

        audit.record(
            AuditAction.STORAGE_LOCATIONS_IMPORTED,
            AuditTargetType.STORAGE_LOCATION,
            null,
            actor,
            "created=%d skipped=%d errors=%d".formatted(created, skipped, errors.size()));

        LOG.info("Topography import done : created={} skipped={} errors={} actor={}",
            created, skipped, errors.size(), actor);
        return new ImportReport(created, skipped, errors);
    }

    private void indexDescendants(TenantId tenant, StorageLocation node, String currentPath, Map<String, StorageLocation> map) {
        for (StorageLocation child : locations.findChildren(tenant, node.id())) {
            String childPath = currentPath + "/" + child.code();
            map.put(childPath, child);
            indexDescendants(tenant, child, childPath, map);
        }
    }

    private static boolean isRowEmpty(Row row) {
        for (int c = 0; c < 4; c++) {
            if (!cellString(row, c).isBlank()) return false;
        }
        return true;
    }

    private static String cellString(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> Optional.ofNullable(cell.getStringCellValue()).orElse("").trim();
            case NUMERIC -> {
                double d = cell.getNumericCellValue();
                yield (d == Math.floor(d)) ? String.valueOf((long) d) : String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private static StorageLocationKind parseKind(String raw, int rowNum, List<String> errors) {
        try {
            return StorageLocationKind.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            errors.add("Ligne %d : kind '%s' invalide (SITE/STOREHOUSE/SHELF/RACK/POSITION)"
                .formatted(rowNum, raw));
            return null;
        }
    }
}
