package com.jokkorm.application.record;

/**
 * Levée par un use case quand la ressource demandée n'existe pas pour le tenant courant.
 * Mappée en {@code 404 problem+json} côté controller.
 */
public class RecordNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RecordNotFoundException(String message) {
        super(message);
    }
}
