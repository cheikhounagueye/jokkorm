package com.jokkorm.application.physical;

/** Mappée en 409 problem+json {@code storage-location-conflict} — code déjà pris pour ce parent. */
public class StorageLocationConflictException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public StorageLocationConflictException(String message) { super(message); }
}
