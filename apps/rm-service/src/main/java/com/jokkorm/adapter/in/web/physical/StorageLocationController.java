package com.jokkorm.adapter.in.web.physical;

import com.jokkorm.application.physical.CreateStorageLocationUseCase;
import com.jokkorm.application.physical.FindStorageLocationTreeUseCase;
import com.jokkorm.domain.physical.StorageLocation;
import com.jokkorm.domain.physical.StorageLocationKind;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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

    public StorageLocationController(
        CreateStorageLocationUseCase createUseCase,
        FindStorageLocationTreeUseCase treeUseCase) {
        this.createUseCase = createUseCase;
        this.treeUseCase = treeUseCase;
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
}
