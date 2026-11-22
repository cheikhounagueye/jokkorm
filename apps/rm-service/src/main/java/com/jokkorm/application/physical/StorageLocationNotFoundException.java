package com.jokkorm.application.physical;

/** Mappée en 404 problem+json {@code storage-location-not-found}. */
public class StorageLocationNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public StorageLocationNotFoundException(String message) { super(message); }
}
