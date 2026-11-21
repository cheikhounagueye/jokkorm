package com.jokkorm.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Clock injectable pour que les tests puissent la geler via {@code Clock.fixed(...)}.
 * Jamais de {@code Instant.now()} direct dans l'application.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
