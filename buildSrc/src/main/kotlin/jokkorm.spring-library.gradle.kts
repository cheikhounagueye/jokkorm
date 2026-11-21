// Convention plugin: Java library that pulls Spring Boot dependency management
// WITHOUT producing a bootable jar. Use for shared libs and for the base of
// jokkorm.spring-boot-service.

plugins {
    id("jokkorm.java-library")
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${Versions.SPRING_BOOT}")
    }
}
