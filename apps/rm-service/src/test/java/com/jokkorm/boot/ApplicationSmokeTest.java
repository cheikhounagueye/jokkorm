package com.jokkorm.boot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Smoke test — proves the Spring Boot entrypoint class exists and the test harness
 * works. The full context load test comes with the first integration test (phase 2).
 */
class ApplicationSmokeTest {

    @Test
    void mainClassExists() {
        assertThat(Application.class).isNotNull();
    }
}
