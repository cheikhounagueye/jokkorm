package com.jokkorm.domain.master;

/**
 * Le maître n'a pas pu répondre de façon exploitable : réseau, authentification refusée,
 * erreur serveur, ou réponse qui n'est pas celle d'une API (page HTML au lieu de JSON).
 *
 * <p>À ne jamais confondre avec « cote inconnue du maître », qui est une réponse valide
 * ({@code Optional.empty()}). Un adapter qui masquerait une panne en « inconnue » ferait
 * croire à l'archiviste qu'une archive n'existe pas.
 */
public class MasterUnreachableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MasterUnreachableException(String message) {
        super(message);
    }

    public MasterUnreachableException(String message, Throwable cause) {
        super(message, cause);
    }
}
