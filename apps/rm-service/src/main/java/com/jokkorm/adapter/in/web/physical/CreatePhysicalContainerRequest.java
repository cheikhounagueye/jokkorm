package com.jokkorm.adapter.in.web.physical;

import jakarta.validation.constraints.Positive;

/**
 * Création d'une boîte : seul le label + la capacité sont saisis par l'utilisateur.
 * Le code-barres est généré automatiquement côté serveur (phase 5K).
 */
public record CreatePhysicalContainerRequest(
    String label,
    @Positive Integer capacity,
    /** Format de boîte (dos 8, 10, 15, 20…). Null = format par défaut du tenant. */
    java.util.UUID boxFormatId) {}
