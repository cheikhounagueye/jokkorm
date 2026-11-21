// Convention plugin: baseline for any Java module (apps/* and future libs/*).
// Framework-agnostic — no Spring here, see jokkorm.spring-library.

plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(Versions.JAVA))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-parameters"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

dependencies {
    "testImplementation"(platform("org.junit:junit-bom:${Versions.JUNIT}"))
    "testImplementation"("org.junit.jupiter:junit-jupiter")
    "testImplementation"("org.assertj:assertj-core:${Versions.ASSERTJ}")
    "testImplementation"("org.mockito:mockito-core:${Versions.MOCKITO}")
    "testImplementation"("com.tngtech.archunit:archunit-junit5:${Versions.ARCHUNIT}")
}
