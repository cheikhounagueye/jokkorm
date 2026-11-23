package com.jokkorm.domain.master;

import java.util.Objects;

/**
 * État physique d'une archive, tel que JokkoRM le renvoie au maître pour affichage
 * (pattern 03 : l'emplacement et les mouvements appartiennent à JokkoRM).
 *
 * <p>Jamais de code-barres : il est réservé aux archivistes (pattern 08), alors que tout
 * utilisateur du maître qui voit l'archive voit aussi ce texte.
 *
 * @param address adresse de terrain, ex. « SALLE-A · Épi E1 · rang 3 · face A · Travée T03 · Tablette 2 »
 *                (null si l'archive n'est pas rangée)
 * @param state   « en magasin », « en prêt, retour prévu le 15/12/2026 », « pas encore rangée »…
 * @param asOf    date et heure de l'état, en clair (ex. « 23/11/2026 14:02 »)
 */
public record PhysicalStatus(String address, String state, String asOf) {

    public PhysicalStatus {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(asOf, "asOf");
    }

    /** Une ligne lisible dans la fiche de l'archive côté maître. */
    public String line() {
        return (address == null ? "" : address + " — ") + state + " (JokkoRM, " + asOf + ")";
    }
}
