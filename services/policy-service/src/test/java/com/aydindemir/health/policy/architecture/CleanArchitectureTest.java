package com.aydindemir.health.policy.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.aydindemir.health.policy",
        importOptions = ImportOption.DoNotIncludeTests.class)
class CleanArchitectureTest {
    @ArchTest
    static final ArchRule domain_has_no_outer_dependencies = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..application..", "..infrastructure..", "..presentation..",
                    "org.springframework..", "jakarta.persistence..");

    @ArchTest
    static final ArchRule application_is_framework_independent = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..infrastructure..", "..presentation..",
                    "org.springframework..", "jakarta..");

    @ArchTest
    static final ArchRule presentation_does_not_bypass_application = noClasses()
            .that().resideInAPackage("..presentation..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..domain..", "..infrastructure..");

    @ArchTest
    static final ArchRule infrastructure_does_not_depend_on_presentation = noClasses()
            .that().resideInAPackage("..infrastructure..")
            .should().dependOnClassesThat().resideInAPackage("..presentation..");
}
