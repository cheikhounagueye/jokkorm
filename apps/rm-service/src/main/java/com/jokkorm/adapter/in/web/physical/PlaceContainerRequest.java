package com.jokkorm.adapter.in.web.physical;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PlaceContainerRequest(@NotNull UUID positionId) {}
