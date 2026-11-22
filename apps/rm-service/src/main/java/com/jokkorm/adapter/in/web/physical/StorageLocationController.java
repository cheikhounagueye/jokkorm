package com.jokkorm.adapter.in.web.physical;

import com.jokkorm.application.physical.CreateStorageLocationUseCase;
import com.jokkorm.application.physical.DeleteStorageLocationUseCase;
import com.jokkorm.application.physical.FindStorageLocationTreeUseCase;
import com.jokkorm.application.physical.ImportTopographyFromExcelUseCase;
import com.jokkorm.application.physical.TopographyExcelTemplateGenerator;
import com.jokkorm.application.physical.UpdateStorageLocationUseCase;
import com.jokkorm.domain.physical.StorageLocation;
import com.jokkorm.domain.physical.StorageLocationKind;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Topographie physique — CRUD des emplacements (phase 5A).
 *
 * <p>Deux endpoints en 5A :
 * <ul>
 *   <li>{@code POST /api/v1/physical/locations} — crée un SITE (si parentId null) ou un enfant.</li>
 *   <li>{@code GET /api/v1/physical/locations/tree} — retourne l'arbre complet de la topographie.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/physical/locations")
@Tag(name = "Storage locations", description = "Topographie physique — sites, magasins, travées, tablettes, positions.")
public class StorageLocationController {

    private final CreateStorageLocationUseCase createUseCase;
    private final FindStorageLocationTreeUseCase treeUseCase;
    private final UpdateStorageLocationUseCase updateUseCase;
    private final DeleteStorageLocationUseCase deleteUseCase;
    private final ImportTopographyFromExcelUseCase importUseCase;
    private final TopographyExcelTemplateGenerator templateGenerator;

    public StorageLocationController(
        CreateStorageLocationUseCase createUseCase,
        FindStorageLocationTreeUseCase treeUseCase,
        UpdateStorageLocationUseCase updateUseCase,
        DeleteStorageLocationUseCase deleteUseCase,
        ImportTopographyFromExcelUseCase importUseCase,
        TopographyExcelTemplateGenerator templateGenerator) {
        this.createUseCase = createUseCase;
        this.treeUseCase = treeUseCase;
        this.updateUseCase = updateUseCase;
        this.deleteUseCase = deleteUseCase;
        this.importUseCase = importUseCase;
        this.templateGenerator = templateGenerator;
    }

    @GetMapping(value = "/import/template.xlsx",
        produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @Operation(
        summary = "Télécharge le modèle Excel vide à remplir pour l'import topographie (phase 5I).",
        description = "Le fichier contient 2 feuilles : Topographie (en-tête + 11 lignes d'exemple "
            + "1 site → 1 magasin → 1 travée → 2 tablettes → 6 positions) + Aide (format détaillé).")
    public ResponseEntity<byte[]> downloadTemplate() {
        byte[] xlsx = templateGenerator.generate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(
            org.springframework.http.ContentDisposition.builder("attachment")
                .filename("jokkorm-topographie-modele.xlsx").build());
        return new ResponseEntity<>(xlsx, headers, HttpStatus.OK);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Importe une topographie depuis un fichier Excel (phase 5I).",
        description = "Upload du fichier au format .xlsx remplissant le modèle. Retourne un "
            + "rapport { created, skippedExisting, errors[] }. Idempotent : un chemin déjà "
            + "en base est ignoré sans erreur. Transaction unique — rollback complet si une "
            + "exception non liée à une ligne survient.")
    public ImportTopographyFromExcelUseCase.ImportReport importExcel(
        @RequestParam("file") MultipartFile file,
        @RequestHeader(value = "X-Actor", defaultValue = "archiviste") String actor) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Fichier vide");
        }
        return importUseCase.execute(file.getInputStream(), actor);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crée un emplacement — SITE racine si parentId est null, enfant sinon.")
    public StorageLocationResponse create(@Valid @RequestBody CreateStorageLocationRequest request) {
        StorageLocation created;
        if (request.parentId() == null) {
            if (request.kind() != StorageLocationKind.SITE) {
                throw new IllegalArgumentException(
                    "A location without parentId must be a SITE, got " + request.kind());
            }
            created = createUseCase.createSite(request.code(), request.label());
        } else {
            created = createUseCase.createChild(
                request.parentId(), request.kind(), request.code(), request.label());
        }
        return StorageLocationResponse.from(created);
    }

    @GetMapping("/tree")
    @Operation(summary = "Arbre complet de la topographie pour le tenant courant.")
    public List<StorageLocationTreeResponse> tree() {
        return treeUseCase.execute().stream()
            .map(StorageLocationTreeResponse::from)
            .toList();
    }

    @PutMapping("/{id}")
    @Operation(
        summary = "Renomme un nœud (admin). Seul le label est modifiable, le code est immuable.",
        description = "Le code fait partie du path dénormalisé — le changer casserait tous les "
            + "descendants et références externes. Pour effacer le libellé, passer `label: null`.")
    public StorageLocationResponse update(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateStorageLocationRequest request,
        @RequestHeader(value = "X-Actor", defaultValue = "admin") String actor) {
        return StorageLocationResponse.from(updateUseCase.execute(id, request.label(), actor));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Supprime un nœud (admin). Refuse si le nœud a des enfants ou contient des boîtes.",
        description = "Protection anti-dégâts : on n'autorise que la suppression de feuilles vides. "
            + "Pour supprimer une branche entière, l'opérateur doit vider récursivement depuis les "
            + "feuilles (déplacer les boîtes puis supprimer POSITION → RACK → ... → SITE).")
    public void delete(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Actor", defaultValue = "admin") String actor) {
        deleteUseCase.execute(id, actor);
    }
}
