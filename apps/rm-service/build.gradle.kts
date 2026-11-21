plugins {
    id("jokkorm.spring-boot-service")
}

description = "rm-service: Records Management physical archive module. Slave of a SAE master (Maarch RM, Alfresco, Nuxeo, generic REST, or standalone)."

dependencies {
    // Phase 3B — Maarch REST adapter uses Spring's RestClient (built on Spring 3.5).
    // No extra dep needed; RestClient is in spring-web already pulled by starter-web.

    // Phase 3B — WireMock pour les tests d'adapter HTTP (Maarch).
    testImplementation("org.wiremock:wiremock-standalone:3.9.1")
}
