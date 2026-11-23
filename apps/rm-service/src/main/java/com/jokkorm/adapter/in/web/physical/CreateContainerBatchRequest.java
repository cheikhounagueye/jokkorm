package com.jokkorm.adapter.in.web.physical;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CreateContainerBatchRequest(
    @NotBlank String reference,
    @Min(1) @Max(1000) int size,
    String labelPattern,
    @Positive Integer defaultCapacity,
    /** Format des boîtes du lot. Null = format par défaut du tenant (pattern 10). */
    java.util.UUID boxFormatId) {}
