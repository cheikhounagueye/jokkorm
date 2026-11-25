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
 *   <li>Règles de regroupement R1 et R2 ({@link BoxGroupingRules}) : service ou sort final différent de
 *       ceux de la boîte, boîte déjà mélangée, sélection mélangée, fiche du maître incomplète ou
 *       illisible.</li>
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
        CONTAINER_CLOSED("container-closed"),
        /** R1 : le dossier n'est pas du service producteur des dossiers de la boîte. */
        SERVICE_MISMATCH("box-service-mismatch"),
        /** R2 : le dossier n'a pas le sort final des dossiers de la boîte. */
        FATE_MISMATCH("box-fate-mismatch"),
        /** La boîte mélange déjà plusieurs services ou sorts finaux : à reconditionner d'abord. */
        BOX_MIXED("box-mixed"),
        /** Le service ou le sort final d'un dossier déjà dans la boîte n'est pas connu. */
        BOX_UNVERIFIABLE("box-unverifiable"),
        /** Mise en boîte groupée : les dossiers choisis mêlent plusieurs services ou sorts finaux. */
        SELECTION_MIXED("selection-mixed"),
        /** Le maître a répondu, mais sa fiche ne dit pas (ou pas lisiblement) le service ou le sort final. */
        MASTER_FACTS_MISSING("master-facts-missing"),
        /** Le maître ne répond pas et JokkoRM n'a pas encore de copie de la fiche. */
        MASTER_FACTS_UNREACHABLE("master-facts-unreachable");

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
