package com.jokkorm.adapter.out.master.maarch;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration du {@link MaarchSaeMasterAdapter}. Phase 3B : config globale unique
 * (un tenant, un Maarch). Phase 4 : promouvoir en config par tenant stockée en DB
 * ({@code tenant_master_config}).
 *
 * <p>Toutes les valeurs sont injectées via {@code jokkorm.master.maarch.*} dans
 * {@code application.yml} ou en variables d'env {@code JOKKORM_MASTER_MAARCH_*}.
 *
 * @param baseUrl  URL racine Maarch (ex. {@code https://maarch.client.lan})
 * @param login    compte technique Maarch (ex. {@code svc-jokkorm})
 * @param password mot de passe (secret ; jamais loggé)
 * @param timeoutMs timeout d'appel en ms (connect + read)
 */
@ConfigurationProperties(prefix = "jokkorm.master.maarch")
public record MaarchProperties(
    String baseUrl, String login, String password, int timeoutMs) {

    public MaarchProperties {
        if (timeoutMs <= 0) {
            timeoutMs = 10_000;
        }
    }
}
