package com.aydindemir.health.claims.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@AnalyzeClasses(packages = "com.aydindemir.health.claims",
        importOptions = ImportOption.DoNotIncludeTests.class)
class CleanArchitectureTest {
    @ArchTest
    static final ArchRule domain_depends_only_on_domain_and_java = classes()
            .that().resideInAPackage("..domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "..domain..");

    @ArchTest
    static final ArchRule application_depends_only_on_inner_layers_and_java = classes()
            .that().resideInAPackage("..application..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    "java..", "..domain..", "..application..");

    @ArchTest
    static final ArchRule presentation_does_not_bypass_application = noClasses()
            .that().resideInAPackage("..presentation..")
            .should().dependOnClassesThat().resideInAnyPackage("..domain..", "..infrastructure..");

    @ArchTest
    static final ArchRule infrastructure_does_not_depend_on_presentation = noClasses()
            .that().resideInAPackage("..infrastructure..")
            .should().dependOnClassesThat().resideInAPackage("..presentation..");
}
