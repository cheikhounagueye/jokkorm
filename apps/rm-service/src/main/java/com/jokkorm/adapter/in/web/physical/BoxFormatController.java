package com.jokkorm.adapter.in.web.physical;

import com.jokkorm.application.physical.BoxFormatAdminUseCase;
import com.jokkorm.domain.physical.BoxFormat;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/physical/box-formats")
@Tag(name = "Box formats", description = "Catalogue de formats de boîtes (pattern 10).")
public class BoxFormatController {

    private final BoxFormatAdminUseCase admin;

    public BoxFormatController(BoxFormatAdminUseCase admin) {
        this.admin = admin;
    }

    public record BoxFormatResponse(UUID id, String code, String label, int spineMm, int depthMm,
                                    int heightMm, BoxFormat.Orientation orientation, BoxFormat.Quality quality,
                                    Integer maxWeightKg, boolean active, boolean isDefault) {
        static BoxFormatResponse from(BoxFormat f) {
            return new BoxFormatResponse(f.id(), f.code(), f.label(), f.spineMm(), f.depthMm(), f.heightMm(),
                f.orientation(), f.quality(), f.maxWeightKg().orElse(null), f.active(), f.isDefault());
        }
    }

    public record CreateRequest(@NotBlank String code, @NotBlank String label, int spineMm, int depthMm,
                                int heightMm, BoxFormat.Quality quality, Integer maxWeightKg) {}

    public record UpdateRequest(String label, BoxFormat.Quality quality, Integer maxWeightKg,
                                Boolean active, Boolean isDefault) {}

    @GetMapping
    @Operation(summary = "Liste le catalogue, du dos le plus fin au plus épais.")
    public List<BoxFormatResponse> list() {
        return admin.list().stream().map(BoxFormatResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajoute un format (boîtes debout). Dos, profondeur, hauteur en mm.")
    public BoxFormatResponse create(
        @Valid @RequestBody CreateRequest r,
        @RequestHeader(value = "X-Actor", defaultValue = "archiviste") String actor) {
        return BoxFormatResponse.from(admin.create(new BoxFormatAdminUseCase.NewFormat(
            r.code(), r.label(), r.spineMm(), r.depthMm(), r.heightMm(), r.quality(), r.maxWeightKg()), actor));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifie libellé, qualité, poids, retire/réactive ou choisit le format par défaut.")
    public BoxFormatResponse update(
        @PathVariable UUID id,
        @RequestBody UpdateRequest r,
        @RequestHeader(value = "X-Actor", defaultValue = "archiviste") String actor) {
        return BoxFormatResponse.from(admin.update(id, new BoxFormatAdminUseCase.FormatChange(
            r.label(), r.quality(), r.maxWeightKg(), r.active(), r.isDefault()), actor));
    }
}
