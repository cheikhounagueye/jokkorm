plugins {
    id("jokkorm.spring-boot-service")
}

description = "rm-service: Records Management physical archive module. Slave of a SAE master (Maarch RM, Alfresco, Nuxeo, generic REST, or standalone)."

dependencies {
    // Nothing specific yet. Phase 1 will vendor commons-tenancy and audit chain.
}
