package com.jokkorm.adapter.in.web.physical;

import com.jokkorm.domain.physical.StorageLocationKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Création d'un emplacement : si {@code parentId} est null, on crée une racine SITE
 * (et {@code kind} doit valoir SITE). Sinon on crée un enfant du bon kind attendu.
 */
public record CreateStorageLocationRequest(
    @NotNull StorageLocationKind kind,
    @NotBlank String code,
    String label,
    UUID parentId) {}
