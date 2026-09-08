package com.aydindemir.health.notification.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.aydindemir.health.notification",
        importOptions = ImportOption.DoNotIncludeTests.class)
class CleanArchitectureTest {
    @ArchTest
    static final ArchRule domain_has_no_outer_dependencies = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..application..", "..infrastructure..", "org.springframework..", "jakarta..");

    @ArchTest
    static final ArchRule application_is_framework_independent = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..infrastructure..", "org.springframework..", "jakarta..");
}
