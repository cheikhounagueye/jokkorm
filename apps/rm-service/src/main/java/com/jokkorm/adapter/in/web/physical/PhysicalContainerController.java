package com.jokkorm.adapter.in.web.physical;

import com.jokkorm.application.physical.CreatePhysicalContainerUseCase;
import com.jokkorm.application.physical.PlaceContainerUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Conteneurs physiques (phase 5B). CRUD minimal + mise en position.
 *
 * <ul>
 *   <li>{@code POST /api/v1/physical/containers} — crée une boîte vide.</li>
 *   <li>{@code PUT /api/v1/physical/containers/{id}/position} — range (ou déplace) la boîte.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/physical/containers")
@Tag(name = "Physical containers", description = "Conteneurs physiques (boîtes d'archives).")
public class PhysicalContainerController {

    private final CreatePhysicalContainerUseCase create;
    private final PlaceContainerUseCase place;

    public PhysicalContainerController(
        CreatePhysicalContainerUseCase create, PlaceContainerUseCase place) {
        this.create = create;
        this.place = place;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crée une boîte vide avec son code-barres.")
    public PhysicalContainerResponse create(@Valid @RequestBody CreatePhysicalContainerRequest request) {
        return PhysicalContainerResponse.from(
            create.execute(request.barcode(), request.label(), request.capacity()));
    }

    @PutMapping("/{id}/position")
    @Operation(summary = "Range (ou déplace) la boîte sur une position (feuille de la topographie).")
    public PhysicalContainerResponse place(
        @PathVariable UUID id,
        @Valid @RequestBody PlaceContainerRequest request) {
        return PhysicalContainerResponse.from(place.execute(id, request.positionId()));
    }
}
