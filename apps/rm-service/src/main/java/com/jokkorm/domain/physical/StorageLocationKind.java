package com.jokkorm.domain.physical;

/**
 * Niveaux de la hiérarchie topographique physique du pattern 2 gardiens
 * (docs/pattern/03-qui-detient-quoi.md — ligne « Topographie des locaux »).
 *
 * <p>Hiérarchie stricte, chaque niveau a UN parent possible :
 * <pre>
 *   SITE        (Diamniadio, Dakar-Centre, …)
 *     └─ STOREHOUSE  (MAG-A, MAG-B, …)
 *          └─ SHELF  (Travée 1, 14, …)
 *               └─ RACK  (Tablette 1 à N)
 *                    └─ POSITION  (slot où une boîte est rangée)
 * </pre>
 */
public enum StorageLocationKind {
    SITE,
    STOREHOUSE,
    SHELF,
    RACK,
    POSITION;

    /** Kind attendu pour un enfant direct, ou {@code null} si ce niveau est terminal. */
    public StorageLocationKind childKind() {
        return switch (this) {
            case SITE -> STOREHOUSE;
            case STOREHOUSE -> SHELF;
            case SHELF -> RACK;
            case RACK -> POSITION;
            case POSITION -> null;
        };
    }

    /** Kind attendu pour le parent direct, ou {@code null} si ce niveau est racine. */
    public StorageLocationKind parentKind() {
        return switch (this) {
            case SITE -> null;
            case STOREHOUSE -> SITE;
            case SHELF -> STOREHOUSE;
            case RACK -> SHELF;
            case POSITION -> RACK;
        };
    }
}
