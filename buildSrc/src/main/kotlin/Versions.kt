/**
 * Single source of truth for dependency versions.
 * Modify here only — never inline a version in a module's build.gradle.kts.
 */
object Versions {
    const val JAVA = 21
    const val SPRING_BOOT = "3.5.0"
    const val SPRING_DEPENDENCY_MGMT = "1.1.7"

    // Test
    const val JUNIT = "5.11.4"
    const val ASSERTJ = "3.27.0"
    const val MOCKITO = "5.14.2"
    const val ARCHUNIT = "1.3.0"
    const val TESTCONTAINERS = "1.21.3"

    // Persistence
    const val LIQUIBASE = "4.31.0"
    const val POSTGRES_JDBC = "42.7.4"

    // Documentation API
    const val SPRINGDOC = "2.7.0"

    // Observability — logs JSON structurés (Loki/Elasticsearch/CloudWatch compat.).
    // Pas dans le BOM Spring Boot : version explicite, 7.4 compatible logback 1.5.
    const val LOGSTASH_ENCODER = "7.4"
}
