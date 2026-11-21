package com.jokkorm.domain.security;

/**
 * Capacités opérationnelles JokkoRM — référentiel granulaire des droits sur les
 * opérations physiques. Dérivé de {@code SaeFunction} d'eversuite-modern avec adaptation
 * au périmètre « module physique » : pas de {@code INGEST} au sens numérique, {@code INGEST}
 * ici = enregistrer un versement physique.
 *
 * <p>Standards : NF Z 42-013 §7 (habilitations graduées), ISO 27001 A.5.15 (least privilege),
 * MoReq2010 §4.6 (séparation des rôles archivistiques).
 *
 * <p>Persistance future (phase 3) : table {@code profile_rm_function}.
 */
public enum RmFunction {

    /** Administration du référentiel : sites, magasins, types de boîte, politique quorum. */
    MANAGE("Administrer le référentiel physique"),

    /** Enregistrer un versement physique, créer un conteneur, attribuer un emplacement. */
    INGEST("Verser dans le magasin"),

    /** Déplacer un conteneur, enregistrer prêt / retour / mouvement physique. */
    MOVE("Déplacer un conteneur"),

    /** Approuver une étape du workflow de destruction (quorum N-eyes). */
    APPROVE("Approuver une étape du workflow"),

    /** Consulter le référentiel physique. Par défaut ouvert à tout user authentifié en phase 2. */
    VIEW("Consulter le référentiel physique");

    private final String label;

    RmFunction(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
