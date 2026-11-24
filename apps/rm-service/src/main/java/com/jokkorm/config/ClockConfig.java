package com.jokkorm.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Clock injectable pour que les tests puissent la geler via {@code Clock.fixed(...)}.
 * Jamais de {@code Instant.now()} direct dans l'application.
 *
 * <p>Elle avance à la microseconde : c'est la précision des horodatages PostgreSQL. Sous Linux
 * l'horloge système donne des nanosecondes ; sans cet arrondi, un instant gardé en mémoire
 * différerait du même instant relu en base (comparaisons, réponses d'API incohérentes).
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
    }
}
