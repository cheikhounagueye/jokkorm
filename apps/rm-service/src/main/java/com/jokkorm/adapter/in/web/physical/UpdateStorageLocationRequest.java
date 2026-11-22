package com.jokkorm.adapter.in.web.physical;

/**
 * Renommage d'un nœud. {@code label} peut être null pour effacer le libellé.
 * Le {@code code} n'est pas modifiable (immuable car partie du path dénormalisé).
 */
public record UpdateStorageLocationRequest(String label) {}
