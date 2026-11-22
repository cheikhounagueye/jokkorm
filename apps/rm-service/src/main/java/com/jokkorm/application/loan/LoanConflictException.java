package com.jokkorm.application.loan;

/** Mappée en 409 — ex. nouvelle demande alors qu'un prêt actif existe déjà. */
public class LoanConflictException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public LoanConflictException(String message) { super(message); }
}
