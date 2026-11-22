package com.jokkorm.adapter.in.web.physical;

import com.jokkorm.domain.physical.MovementKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RecordMovementRequest(
    @NotNull MovementKind kind,
    @NotBlank String actor,
    String motive) {}
