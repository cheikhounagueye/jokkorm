package com.jokkorm.application.physical;

import com.jokkorm.application.audit.AuditChainService;
import com.jokkorm.commons.role.RoleContext;
import com.jokkorm.commons.role.UserRole;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.audit.AuditAction;
import com.jokkorm.domain.audit.AuditTargetType;
import com.jokkorm.domain.physical.FurnitureType;
import com.jokkorm.domain.physical.PhysicalSpec;
import com.jokkorm.domain.physical.StorageLocation;
import com.jokkorm.domain.physical.StorageLocationKind;
import com.jokkorm.domain.physical.StorageLocationRepository;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
 * Importe un fichier Excel de topographie (phase 5I, étendu en 5T.C — pattern 10 § 4.4).
 *
 * <p>Une ligne par nœud. Les quatre premières colonnes sont celles de 5I ; les suivantes,
 * toutes facultatives, portent les mesures (en centimètres) :
 * <pre>
 *   kind | code | label | parent_path | type_mobilier | longueur_cm | profondeur_face_cm |
 *   hauteur_libre_cm | hauteur_meuble_cm | faces | epi | rang | face | nb_tablettes
 * </pre>
 *
 * <ul>
 *   <li>Nœud nouveau : créé avec ses mesures.</li>
 *   <li>Nœud existant sans mesure sur la ligne : ignoré (rejouer un fichier ne change rien).</li>
 *   <li>Nœud existant avec mesures : <b>fusion</b> — une case remplie remplace, une case vide garde
 *       la valeur actuelle. Un ancien fichier à quatre colonnes n'efface donc rien.</li>
 *   <li>{@code nb_tablettes} sur une travée qui n'a encore aucune tablette : crée ces tablettes,
 *       un emplacement chacune.</li>
 *   <li>Chaque ligne est contrôlée <b>avant</b> d'écrire (boîtes déjà rangées qui ne tiendraient
 *       plus, bornes) : une ligne refusée est signalée et ne laisse aucune trace.</li>
 * </ul>
 */
@Service
public class ImportTopographyFromExcelUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(ImportTopographyFromExcelUseCase.class);
    private static final int COLUMNS = 14;

    private final StorageLocationRepository locations;
    private final ShelfSpaceService space;
    private final AuditChainService audit;
    private final Clock clock;

    public ImportTopographyFromExcelUseCase(
        StorageLocationRepository locations,
        ShelfSpaceService space,
        AuditChainService audit,
        Clock clock) {
        this.locations = locations;
        this.space = space;
        this.audit = audit;
        this.clock = clock;
    }

    public record ImportReport(int created, int updated, int skippedExisting, List<String> errors) {}

    /** Mesures lues sur une ligne ; null = case vide. */
    record Measures(Integer lengthMm, FurnitureType furnitureType, Integer depthMm, Integer clearanceMm,
                    Integer heightMm, Integer faces, String aisleCode, Integer aisleOrder, String aisleSide,
                    Integer tablettes) {
        boolean isEmpty() {
            return lengthMm == null && furnitureType == null && depthMm == null && clearanceMm == null
                && heightMm == null && faces == null && aisleCode == null && aisleOrder == null
                && aisleSide == null && tablettes == null;
        }

        /** Fusion : ce qui est rempli remplace, le reste est gardé. */
        PhysicalSpec mergeInto(PhysicalSpec old) {
            return new PhysicalSpec(
                furnitureType != null ? furnitureType : old.furnitureType(),
                depthMm != null ? depthMm : old.depthMm(),
                clearanceMm != null ? clearanceMm : old.clearanceMm(),
                heightMm != null ? heightMm : old.heightMm(),
                faces != null ? faces : old.faces(),
                aisleCode != null ? aisleCode : old.aisleCode(),
                aisleOrder != null ? aisleOrder : old.aisleOrder(),
                aisleSide != null ? aisleSide : old.aisleSide());
        }
    }

    @Transactional
    public ImportReport execute(InputStream excelStream, String actor) {
        // Le fichier peut désormais changer des dimensions : même porte que PUT /spec.
        RoleContext.requireAny(UserRole.ARCHIVIST, UserRole.ARCHIVES_HEAD);
        TenantId tenant = TenantContext.current();
        Instant now = clock.instant();
        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        Map<String, StorageLocation> pathToNode = new HashMap<>();
        for (StorageLocation root : locations.findRoots(tenant)) {
            pathToNode.put(root.code(), root);
            indexDescendants(tenant, root, root.code(), pathToNode);
        }

        try (Workbook workbook = new XSSFWorkbook(excelStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int r = 1; r <= sheet.getLastRowNum(); r++) { // r=0 = en-tête
                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row)) continue;
                int line = r + 1;
                try {
                    String kindStr = cellString(row, 0);
                    String code = cellString(row, 1);
                    String label = cellString(row, 2);
                    String parentPath = cellString(row, 3).toUpperCase(Locale.ROOT);
                    if (kindStr.isBlank() || code.isBlank()) {
                        errors.add("Ligne %d : kind et code obligatoires".formatted(line));
                        continue;
                    }
                    StorageLocationKind kind = parseKind(kindStr);
                    Measures m = readMeasures(row);
                    if (m.lengthMm() != null && !StorageLocation.hasLength(kind)) {
                        throw new IllegalArgumentException("un " + kind + " n'a pas de longueur");
                    }
                    if (!StorageLocation.hasSpec(kind) && !m.mergeInto(PhysicalSpec.EMPTY).isEmpty()) {
                        throw new IllegalArgumentException("un " + kind + " n'a pas de mobilier ni de dimensions");
                    }
                    if (m.tablettes() != null && kind != StorageLocationKind.SHELF) {
                        throw new IllegalArgumentException("nb_tablettes ne s'emploie que sur une travée (SHELF)");
                    }
                    String fullPath = parentPath.isBlank() ? code.toUpperCase(Locale.ROOT)
                        : parentPath + "/" + code.toUpperCase(Locale.ROOT);

                    StorageLocation existing = pathToNode.get(fullPath);
                    if (existing != null) {
                        if (m.isEmpty()) {
                            skipped++;
                            continue;
                        }
                        if (existing.kind() != kind) {
                            throw new IllegalArgumentException("%s existe déjà comme %s, pas %s".formatted(fullPath, existing.kind(), kind));
                        }
                        Integer newLength = m.lengthMm() != null ? m.lengthMm() : existing.lengthMm().orElse(null);
                        PhysicalSpec merged = StorageLocation.hasSpec(kind)
                            ? m.mergeInto(existing.spec()).requireAllowedFor(kind) : existing.spec();
                        List<String> problems = space.problemsIf(tenant, existing, newLength, merged);
                        if (!problems.isEmpty()) {
                            errors.add("Ligne %d : %s — ligne ignorée".formatted(line, problems.get(0)));
                            continue;
                        }
                        existing.changeLength(newLength, now);
                        if (StorageLocation.hasSpec(kind)) existing.changeSpec(merged, now);
                        pathToNode.put(fullPath, locations.save(existing));
                        updated++;
                    } else {
                        StorageLocation toCreate;
                        if (parentPath.isBlank()) {
                            if (kind != StorageLocationKind.SITE) {
                                throw new IllegalArgumentException("un nœud sans parent_path doit être SITE (reçu " + kind + ")");
                            }
                            toCreate = StorageLocation.createSite(tenant, code, label.isBlank() ? null : label, now);
                        } else {
                            StorageLocation parent = pathToNode.get(parentPath);
                            if (parent == null) {
                                throw new IllegalArgumentException("parent_path '%s' introuvable (ni importé plus haut ni déjà en base)".formatted(parentPath));
                            }
                            toCreate = StorageLocation.createChild(parent, kind, code, label.isBlank() ? null : label, now);
                        }
                        // Mesures appliquées avant l'enregistrement : rien à contrôler, le nœud est vide.
                        if (m.lengthMm() != null) toCreate.changeLength(m.lengthMm(), now);
                        if (StorageLocation.hasSpec(kind)) toCreate.changeSpec(m.mergeInto(PhysicalSpec.EMPTY), now);
                        pathToNode.put(fullPath, locations.save(toCreate));
                        created++;
                    }

                    if (m.tablettes() != null) {
                        created += createTablettes(tenant, pathToNode.get(fullPath), fullPath, m.tablettes(), now, pathToNode);
                    }
                } catch (RuntimeException e) {
                    errors.add("Ligne %d : %s".formatted(line, e.getMessage()));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read Excel file: " + e.getMessage(), e);
        }

        // Le journal chaîné exige une cible : chaque import a son identifiant (cité dans le détail).
        java.util.UUID importId = java.util.UUID.randomUUID();
        audit.record(AuditAction.STORAGE_LOCATIONS_IMPORTED, AuditTargetType.STORAGE_LOCATION, importId, actor,
            "import=%s created=%d updated=%d skipped=%d errors=%d".formatted(importId, created, updated, skipped, errors.size()));
        LOG.info("Topography import done : created={} updated={} skipped={} errors={} actor={}",
            created, updated, skipped, errors.size(), actor);
        return new ImportReport(created, updated, skipped, errors);
    }

    /** Tablettes 1..n (un emplacement 01 chacune), seulement si la travée n'en a aucune. */
    private int createTablettes(TenantId tenant, StorageLocation shelf, String shelfPath, int n, Instant now,
                                Map<String, StorageLocation> pathToNode) {
        if (n < 1 || n > 20) throw new IllegalArgumentException("nb_tablettes : entre 1 et 20, reçu " + n);
        boolean hasRacks = locations.findChildren(tenant, shelf.id()).stream()
            .anyMatch(c -> c.kind() == StorageLocationKind.RACK);
        if (hasRacks) return 0;
        int created = 0;
        for (int i = 1; i <= n; i++) {
            StorageLocation rack = locations.save(StorageLocation.createChild(shelf, StorageLocationKind.RACK, String.valueOf(i), null, now));
            pathToNode.put(shelfPath + "/" + rack.code(), rack);
            StorageLocation pos = locations.save(StorageLocation.createChild(rack, StorageLocationKind.POSITION, "01", null, now));
            pathToNode.put(shelfPath + "/" + rack.code() + "/" + pos.code(), pos);
            created += 2;
        }
        return created;
    }

    static Measures readMeasures(Row row) {
        String faces = cellString(row, 9).toLowerCase(Locale.ROOT);
        return new Measures(
            cm(cellString(row, 5), "longueur_cm"),
            furniture(cellString(row, 4)),
            cm(cellString(row, 6), "profondeur_face_cm"),
            cm(cellString(row, 7), "hauteur_libre_cm"),
            cm(cellString(row, 8), "hauteur_meuble_cm"),
            switch (faces) {
                case "" -> null;
                case "1", "simple" -> 1;
                case "2", "double" -> 2;
                default -> throw new IllegalArgumentException("faces : 1/simple ou 2/double, reçu " + faces);
            },
            blank(cellString(row, 10)),
            integer(cellString(row, 11), "rang"),
            blank(cellString(row, 12)),
            integer(cellString(row, 13), "nb_tablettes"));
    }

    /** « 35 », « 35,5 », « 35.5 » centimètres → millimètres. */
    static Integer cm(String raw, String column) {
        if (raw.isBlank()) return null;
        try {
            return (int) Math.round(Double.parseDouble(raw.replace(',', '.')) * 10);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(column + " : nombre en centimètres attendu, reçu « " + raw + " »");
        }
    }

    private static Integer integer(String raw, String column) {
        if (raw.isBlank()) return null;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(column + " : nombre entier attendu, reçu « " + raw + " »");
        }
    }

    /** Nom technique (FIXED_SHELVING) ou libellé français (« rayonnage fixe », « armoire »…). */
    static FurnitureType furniture(String raw) {
        if (raw.isBlank()) return null;
        String k = Normalizer.normalize(raw, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", " ").trim();
        return switch (k) {
            case "fixed shelving", "rayonnage fixe", "rayonnage" -> FurnitureType.FIXED_SHELVING;
            case "mobile shelving", "rayonnage mobile", "compactus" -> FurnitureType.MOBILE_SHELVING;
            case "cabinet", "armoire" -> FurnitureType.CABINET;
            case "fire safe", "armoire forte", "armoire ignifuge" -> FurnitureType.FIRE_SAFE;
            case "plan chest", "meuble a plans" -> FurnitureType.PLAN_CHEST;
            case "pallet rack", "rack a palettes" -> FurnitureType.PALLET_RACK;
            default -> throw new IllegalArgumentException("type_mobilier inconnu : « " + raw
                + " » (rayonnage fixe, rayonnage mobile, armoire, armoire forte, meuble à plans, rack à palettes)");
        };
    }

    private static String blank(String s) {
        return s.isBlank() ? null : s;
    }

    private void indexDescendants(TenantId tenant, StorageLocation node, String currentPath, Map<String, StorageLocation> map) {
        for (StorageLocation child : locations.findChildren(tenant, node.id())) {
            String childPath = currentPath + "/" + child.code();
            map.put(childPath, child);
            indexDescendants(tenant, child, childPath, map);
        }
    }

    private static boolean isRowEmpty(Row row) {
        for (int c = 0; c < COLUMNS; c++) {
            if (!cellString(row, c).isBlank()) return false;
        }
        return true;
    }

    static String cellString(Row row, int col) {
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

    private static StorageLocationKind parseKind(String raw) {
        try {
            return StorageLocationKind.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("kind '%s' invalide (SITE/STOREHOUSE/SHELF/RACK/POSITION)".formatted(raw));
        }
    }
}
