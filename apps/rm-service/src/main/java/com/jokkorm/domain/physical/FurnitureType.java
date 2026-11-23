package com.jokkorm.domain.physical;

/**
 * Type de mobilier d'une travée (pattern 10 § 2.3). Aucune dimension n'est imposée par le
 * type : le mobilier, souvent fabriqué localement, se mesure sur place.
 */
public enum FurnitureType {
    /** Rayonnage fixe, simple ou double face. */
    FIXED_SHELVING,
    /** Rayonnage mobile (compactus) : une seule allée mobile, plancher renforcé. */
    MOBILE_SHELVING,
    /** Armoire de bureau, souvent verrouillée. */
    CABINET,
    /** Armoire forte ou ignifuge, pour les pièces sensibles. */
    FIRE_SAFE,
    /** Meuble à plans (tiroirs ; rangement à plat en v2). */
    PLAN_CHEST,
    /** Rack à palettes (tiers archiveurs ; conteneurs en v2). */
    PALLET_RACK
}
