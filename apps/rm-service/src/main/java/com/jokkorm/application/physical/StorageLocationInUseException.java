package com.jokkorm.application.physical;

/**
 * Mappée en 409 problem+json {@code storage-location-in-use} — tentative de supprimer
 * un nœud qui a des enfants ou qui contient des boîtes.
 */
public class StorageLocationInUseException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public StorageLocationInUseException(String message) { super(message); }
}
