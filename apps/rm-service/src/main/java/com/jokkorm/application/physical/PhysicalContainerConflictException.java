package com.jokkorm.application.physical;

/** Mappée en 409 problem+json {@code container-conflict} — barcode déjà pris. */
public class PhysicalContainerConflictException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public PhysicalContainerConflictException(String message) { super(message); }
}
