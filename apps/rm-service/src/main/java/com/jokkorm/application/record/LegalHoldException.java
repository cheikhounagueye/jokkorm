package com.jokkorm.application.record;

/** Une sortie physique est refusée à cause d'un gel contentieux (ou d'un gel invérifiable). 409. */
public class LegalHoldException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LegalHoldException(String message) {
        super(message);
    }
}
