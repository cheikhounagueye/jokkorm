package com.jokkorm.domain.master;

/**
 * Le maître a répondu, mais refuse durablement l'écriture (archive inconnue, champ absent de
 * son profil d'archivage, droits du compte de service…). Inutile de réessayer tel quel :
 * l'envoi passe en « refusé » avec la raison, visible de l'archiviste.
 */
public class MasterRejectedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MasterRejectedException(String message) {
        super(message);
    }
}
