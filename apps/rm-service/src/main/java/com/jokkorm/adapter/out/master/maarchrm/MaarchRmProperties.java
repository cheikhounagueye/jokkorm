package com.jokkorm.adapter.out.master.maarchrm;

import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connexion à Maarch RM 2.x (framework laabs). Valeurs en variables d'environnement
 * {@code JOKKORM_MAARCHRM_*} (voir application.yml). Aucun secret dans le dépôt.
 *
 * @param baseUrl            URL racine, ex. {@code http://localhost:8088}
 * @param serviceClientToken valeur {@code SERVICE_CLIENT_TOKEN} du vhost Maarch : le
 *                           User-Agent doit la contenir, sinon Maarch sert son interface
 *                           web (HTML) au lieu de l'API JSON
 * @param authMode           {@code basic} (utilisateur rattaché au service d'archives) ou
 *                           {@code service-token} (compte de service, cookie LAABS-AUTH)
 * @param login              utilisateur Maarch en mode basic
 * @param password           mot de passe en mode basic (jamais loggé)
 * @param serviceToken       jeton du compte de service en mode service-token (jamais loggé)
 * @param timeoutMs          délai max d'un appel
 * @param locationField      champ de description Maarch où s'affiche l'état physique (tranche 2a)
 */
@ConfigurationProperties(prefix = "jokkorm.master.maarchrm")
public record MaarchRmProperties(
    String baseUrl,
    String serviceClientToken,
    String authMode,
    String login,
    String password,
    String serviceToken,
    int timeoutMs,
    String locationField) {

    public enum AuthMode { BASIC, SERVICE_TOKEN }

    @org.springframework.boot.context.properties.bind.ConstructorBinding
    public MaarchRmProperties {
        baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        serviceClientToken = serviceClientToken == null || serviceClientToken.isBlank() ? "service" : serviceClientToken.trim();
        authMode = authMode == null || authMode.isBlank() ? "basic" : authMode.trim();
        if (timeoutMs <= 0) timeoutMs = 10_000;
        locationField = locationField == null || locationField.isBlank() ? "localisation_physique" : locationField.trim();
    }

    /** Sans champ de localisation précisé : {@code localisation_physique}. */
    public MaarchRmProperties(String baseUrl, String serviceClientToken, String authMode, String login,
                              String password, String serviceToken, int timeoutMs) {
        this(baseUrl, serviceClientToken, authMode, login, password, serviceToken, timeoutMs, null);
    }

    public AuthMode mode() {
        return AuthMode.valueOf(authMode.toUpperCase(Locale.ROOT).replace('-', '_'));
    }

    /** Qui se connecte, pour les journaux (jamais le secret). */
    public String principal() {
        return mode() == AuthMode.BASIC ? login : "compte de service";
    }
}
