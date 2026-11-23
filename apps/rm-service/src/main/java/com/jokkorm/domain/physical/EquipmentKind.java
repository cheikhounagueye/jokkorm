package com.jokkorm.domain.physical;

/** Équipements de sécurité et de conservation d'une salle d'archives. */
public enum EquipmentKind {
    FIRE_EXTINGUISHER,
    SMOKE_DETECTOR,
    EMERGENCY_EXIT,
    THERMO_HYGROMETER;

    /** Les détecteurs sont au plafond, tout le reste est fixé aux murs ou au sol. */
    public boolean ceilingMounted() {
        return this == SMOKE_DETECTOR;
    }
}
