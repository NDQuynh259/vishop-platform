package com.vishop.audit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class AuditArchitectureTest {
  @Test
  void domainIsFrameworkIndependentAndAdaptersUseApplicationBoundary() {
    var classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.vishop.audit");
    noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework..",
            "jakarta..",
            "com.fasterxml.jackson..",
            "..application..",
            "..infrastructure..",
            "..presentation..")
        .check(classes);
    noClasses()
        .that()
        .resideInAPackage("..application..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..infrastructure..",
            "..presentation..",
            "jakarta.persistence..",
            "org.springframework.data..",
            "org.apache.kafka..",
            "com.fasterxml.jackson..",
            "..common.persistence..")
        .check(classes);
    noClasses()
        .that()
        .resideInAPackage("..presentation..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..infrastructure.persistence..",
            "com.vishop.audit..domain..",
            "..application.port..",
            "..common.persistence..")
        .check(classes);
  }
}
