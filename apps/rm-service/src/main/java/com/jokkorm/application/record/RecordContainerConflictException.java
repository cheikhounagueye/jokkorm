package com.jokkorm.application.record;

/**
 * Le rangement demandé contredit la règle « un dossier, une seule boîte » (HTTP 409).
 *
 * <ul>
 *   <li>{@link Reason#ALREADY_IN_THIS_BOX} — le dossier est déjà dans cette boîte : rien à faire.</li>
 *   <li>{@link Reason#IN_ANOTHER_BOX} — il est dans une autre boîte : seul un transfert explicite
 *       peut l'en sortir.</li>
 *   <li>{@link Reason#TRANSFER_BLOCKED} — transfert impossible (une des deux boîtes est en prêt).</li>
 *   <li>{@link Reason#CONTAINER_ON_LOAN} — la boîte d'arrivée est en prêt : son contenu est promis.</li>
 *   <li>{@link Reason#CONTAINER_FULL} — la boîte a atteint sa capacité (règle R4).</li>
 *   <li>{@link Reason#CONTAINER_CLOSED} — la boîte est fermée ou détruite : elle ne reçoit plus rien.</li>
 * </ul>
 *
 * <p>La boîte actuelle (code-barres, libellé, adresse) accompagne la réponse : l'écran peut
 * dire où est le dossier et proposer le transfert.
 */
public class RecordContainerConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public enum Reason {
        ALREADY_IN_THIS_BOX("record-already-in-container"),
        IN_ANOTHER_BOX("record-in-other-container"),
        TRANSFER_BLOCKED("record-transfer-blocked"),
        CONTAINER_ON_LOAN("container-on-loan"),
        CONTAINER_FULL("container-full"),
        CONTAINER_CLOSED("container-closed");

        private final String slug;

        Reason(String slug) {
            this.slug = slug;
        }

        public String slug() {
            return slug;
        }
    }

    private final Reason reason;
    private final String currentBarcode;
    private final String currentLabel;
    private final String currentAddress;

    public RecordContainerConflictException(
        Reason reason, String message, String currentBarcode, String currentLabel, String currentAddress) {
        super(message);
        this.reason = reason;
        this.currentBarcode = currentBarcode;
        this.currentLabel = currentLabel;
        this.currentAddress = currentAddress;
    }

    public Reason reason() {
        return reason;
    }

    public String currentBarcode() {
        return currentBarcode;
    }

    public String currentLabel() {
        return currentLabel;
    }

    public String currentAddress() {
        return currentAddress;
    }
}
