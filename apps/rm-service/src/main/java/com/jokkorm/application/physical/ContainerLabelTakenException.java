package com.jokkorm.application.physical;

/**
 * Libellé de boîte déjà porté par une autre boîte de l'espace (règle « un libellé, une
 * seule boîte »). Mappée en 409 problem+json {@code container-label-taken} ; la boîte qui
 * porte déjà le libellé accompagne la réponse pour l'écran.
 */
public class ContainerLabelTakenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String currentBarcode;
    private final String currentLabel;
    private final String currentAddress;
    private final long count;

    public ContainerLabelTakenException(String message, String currentBarcode, String currentLabel,
                                        String currentAddress, long count) {
        super(message);
        this.currentBarcode = currentBarcode;
        this.currentLabel = currentLabel;
        this.currentAddress = currentAddress;
        this.count = count;
    }

    public String currentBarcode() { return currentBarcode; }
    public String currentLabel() { return currentLabel; }
    public String currentAddress() { return currentAddress; }
    /** Nombre de boîtes déjà concernées (1 pour une boîte seule, la série entière pour un lot). */
    public long count() { return count; }
}
