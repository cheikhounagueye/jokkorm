package com.jokkorm.application.physical;

/**
 * Retrait refusé : l'emplacement scanné n'est pas celui où la boîte est enregistrée
 * (409 container-not-at-position). Porte l'adresse enregistrée pour l'afficher au magasinier.
 */
public class ContainerNotAtPositionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String currentAddress;

    public ContainerNotAtPositionException(String message, String currentAddress) {
        super(message);
        this.currentAddress = currentAddress;
    }

    public String currentAddress() {
        return currentAddress;
    }
}
