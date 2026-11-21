package com.jokkorm.adapter.in.web.event;

import com.jokkorm.application.record.ApplyLegalHoldUseCase.Action;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Payload de l'événement maître « legal-hold » : gel ou dégel contentieux sur une
 * archive. Côté JokkoRM, un gel bloque toute sortie physique.
 */
public record LegalHoldRequest(@NotBlank String cote, @NotNull Action action) {}
