package com.jokkorm.domain.common;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * Codes courts à scanner, imprimés sur les étiquettes : un préfixe qui dit ce que c'est
 * ({@code EMP-} emplacement, {@code PRT-} fantôme de prêt) puis 8 caractères en base 32 de
 * Crockford (sans I, L, O, U : rien à confondre à la lecture ou à la saisie).
 */
public final class ShortCode {

    private static final String CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int LENGTH = 8;

    private ShortCode() {}

    /** Nouveau code au hasard, ex. {@code random("PRT-")} → {@code PRT-7K2M9QXA}. */
    public static String random(String prefix) {
        StringBuilder sb = new StringBuilder(prefix);
        for (int i = 0; i < LENGTH; i++) sb.append(CROCKFORD.charAt(RANDOM.nextInt(CROCKFORD.length())));
        return sb.toString();
    }

    /** Forme attendue d'un code de ce préfixe (les codes repris de l'existant sont en hexadécimal). */
    public static Pattern pattern(String prefix) {
        return Pattern.compile("^" + Pattern.quote(prefix) + "[0-9A-Z]{" + LENGTH + "}$");
    }
}
