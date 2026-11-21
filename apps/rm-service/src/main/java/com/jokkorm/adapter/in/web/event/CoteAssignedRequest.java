package com.jokkorm.adapter.in.web.event;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload de l'événement maître « cote-assigned » : le maître informe JokkoRM qu'une
 * nouvelle cote a été attribuée à un dossier clos et doit être provisionnée côté
 * magasin physique.
 */
public record CoteAssignedRequest(@NotBlank String cote) {}
