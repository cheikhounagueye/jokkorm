package com.jokkorm.application.physical;

/** Mappée en 404 problem+json {@code batch-not-found}. */
public class BatchNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public BatchNotFoundException(String message) { super(message); }
}
