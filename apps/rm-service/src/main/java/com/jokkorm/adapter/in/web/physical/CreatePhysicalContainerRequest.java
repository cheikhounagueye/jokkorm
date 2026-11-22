package com.jokkorm.adapter.in.web.physical;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CreatePhysicalContainerRequest(
    @NotBlank String barcode,
    String label,
    @Positive Integer capacity) {}
