package com.jokkorm.application.physical;

/** Code scanné qui n'est ni une boîte ni un emplacement de l'espace courant (404 scan-code-unknown). */
public class ScanCodeUnknownException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ScanCodeUnknownException(String message) {
        super(message);
    }
}
