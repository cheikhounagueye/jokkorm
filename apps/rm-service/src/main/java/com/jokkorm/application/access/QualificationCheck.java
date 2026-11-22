package com.jokkorm.application.access;

/**
 * Résultat d'un contrôle automatique lors de la qualification (phase 5P.E).
 *
 * <p>Codes stables pour l'UI :
 * <ul>
 *   <li>{@code CONTAINER_EXISTS}, {@code NOT_DESTROYED}, {@code NO_ACTIVE_LOAN},
 *       {@code NO_ACTIVE_REQUEST}, {@code BUFFER_ZONE}, {@code DUA_STATUS},
 *       {@code COMMUNICABILITY}.</li>
 * </ul>
 */
public record QualificationCheck(
    String code,
    Severity severity,
    boolean passed,
    String message) {

    public enum Severity {
        /** Bloquant : la qualification ne peut pas valider. */
        BLOCKING,
        /** Warning : l'archiviste peut quand même valider (avec notes). */
        WARNING,
        /** Info : juste pour contexte. */
        INFO
    }

    public static QualificationCheck pass(String code) {
        return new QualificationCheck(code, Severity.INFO, true, "OK");
    }

    public static QualificationCheck block(String code, String message) {
        return new QualificationCheck(code, Severity.BLOCKING, false, message);
    }

    public static QualificationCheck warn(String code, String message) {
        return new QualificationCheck(code, Severity.WARNING, false, message);
    }

    public boolean isBlocking() { return severity == Severity.BLOCKING && !passed; }
}
