// Convention plugin: applied to runnable Spring Boot services (apps/*).
// Inherits everything from spring-library and adds Boot packaging + actuator +
// web + security + JPA + Liquibase + OpenAPI + observability baseline.

plugins {
    id("jokkorm.spring-library")
    id("org.springframework.boot")
}

dependencies {
    "implementation"("org.springframework.boot:spring-boot-starter-web")
    "implementation"("org.springframework.boot:spring-boot-starter-security")
    "implementation"("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    "implementation"("org.springframework.boot:spring-boot-starter-validation")
    "implementation"("org.springframework.boot:spring-boot-starter-data-jpa")
    "implementation"("org.springframework.boot:spring-boot-starter-actuator")
    "implementation"("org.liquibase:liquibase-core")

    "implementation"("org.postgresql:postgresql:${Versions.POSTGRES_JDBC}")

    // OpenAPI — le livrable n°1 du module. Doc exposée sur /swagger-ui.
    "implementation"("org.springdoc:springdoc-openapi-starter-webmvc-ui:${Versions.SPRINGDOC}")

    // Observability — Prometheus scrape + logs JSON structurés.
    "implementation"("io.micrometer:micrometer-registry-prometheus")
    "implementation"("net.logstash.logback:logstash-logback-encoder:${Versions.LOGSTASH_ENCODER}")

    "testImplementation"("org.springframework.boot:spring-boot-starter-test")
    "testImplementation"("org.springframework.security:spring-security-test")
    "testImplementation"("org.testcontainers:junit-jupiter:${Versions.TESTCONTAINERS}")
    "testImplementation"("org.testcontainers:postgresql:${Versions.TESTCONTAINERS}")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveClassifier.set("boot")
}

// Testcontainers wiring — compat Docker 25+ / Orbstack.
tasks.withType<Test>().configureEach {
    listOf("DOCKER_HOST", "DOCKER_API_VERSION", "TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE",
        "TESTCONTAINERS_HOST_OVERRIDE").forEach { key ->
        System.getenv(key)?.let { environment(key, it) }
    }
    if (System.getenv("DOCKER_API_VERSION").isNullOrBlank()) {
        environment("DOCKER_API_VERSION", "1.43")
        systemProperty("api.version", "1.43")
    }
    val orbstackSock = "${System.getProperty("user.home")}/.orbstack/run/docker.sock"
    if (System.getenv("DOCKER_HOST").isNullOrBlank() && file(orbstackSock).exists()) {
        environment("DOCKER_HOST", "unix://$orbstackSock")
    }
}
