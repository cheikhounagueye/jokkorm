package com.jokkorm.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Garde-fous architecturaux ADR-0001 et ADR-0002.
 *
 * <p>Si une classe du domaine se met à importer Spring, JPA ou Jackson, la CI casse ici.
 * C'est la seule frontière qui tient quand l'équipe grandit.
 */
@AnalyzeClasses(packages = "com.jokkorm", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule domain_must_not_depend_on_spring =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..")
            .because("ADR-0001 : le domaine est framework-light. Pas d'import Spring dans domain/.");

    @ArchTest
    static final ArchRule domain_must_not_depend_on_jpa =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("jakarta.persistence..", "org.hibernate..")
            .because("ADR-0001 : entité JPA ≠ entité domaine. Mapping en adapter/out/persistence/.");

    @ArchTest
    static final ArchRule domain_must_not_depend_on_jackson =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.fasterxml.jackson..")
            .because("ADR-0001 : pas de sérialisation dans le domaine. Mapping en adapter/in/web/.");

    @ArchTest
    static final ArchRule application_must_not_depend_on_web_or_persistence =
        noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "com.jokkorm.adapter.in..",
                "com.jokkorm.adapter.out..")
            .because("ADR-0001 : l'application dépend des ports du domaine, pas des adapters.");
}
