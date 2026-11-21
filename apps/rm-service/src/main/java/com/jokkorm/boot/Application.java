package com.jokkorm.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * JokkoRM — Records Management physical archive module.
 *
 * <p>Boot entrypoint. The package {@code com.jokkorm} is the scan root for Spring,
 * JPA entities, and Liquibase changelogs. See docs/adr/0001-modular-monolith-hexagonal.md.
 */
@SpringBootApplication(scanBasePackages = "com.jokkorm")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
