package com.jokkorm.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JokkoRM — Records Management physical archive module.
 *
 * <p>Boot entrypoint. Trois scans explicites sur {@code com.jokkorm} :
 * <ul>
 *   <li>Beans ({@code scanBasePackages}) : couvre services, controllers, adapters.</li>
 *   <li>Entités JPA ({@code @EntityScan}) : par défaut Spring Boot scanne depuis le
 *       package du main, soit {@code com.jokkorm.boot} seulement — insuffisant.</li>
 *   <li>Spring Data Repositories ({@code @EnableJpaRepositories}) : même raison.</li>
 * </ul>
 * Voir docs/adr/0001-modular-monolith-hexagonal.md.
 */
@SpringBootApplication(scanBasePackages = "com.jokkorm")
@EntityScan(basePackages = "com.jokkorm")
@EnableJpaRepositories(basePackages = "com.jokkorm")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
