package com.jokkorm.domain.physical;

/**
 * État du conteneur physique (boîte d'archives).
 *
 * <ul>
 *   <li>{@link #EMPTY} — boîte vide, prête à recevoir des dossiers.</li>
 *   <li>{@link #OPEN} — contient au moins un dossier, peut encore en recevoir.</li>
 *   <li>{@link #SEALED} — scellée, ne reçoit plus de nouveaux dossiers ; sortie contrôlée.</li>
 *   <li>{@link #DESTROYED} — a été détruite physiquement avec le reste de son contenu (terminal).</li>
 * </ul>
 */
public enum ContainerState {
    EMPTY,
    OPEN,
    SEALED,
    DESTROYED
}
