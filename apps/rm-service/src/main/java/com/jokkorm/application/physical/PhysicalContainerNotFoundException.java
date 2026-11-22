package com.jokkorm.application.physical;

/** Mappée en 404 problem+json {@code container-not-found}. */
public class PhysicalContainerNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public PhysicalContainerNotFoundException(String message) { super(message); }
}
