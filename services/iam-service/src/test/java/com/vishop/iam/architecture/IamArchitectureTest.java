package com.vishop.iam.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.vishop.iam", importOptions = ImportOption.DoNotIncludeTests.class)
class IamArchitectureTest {
  @ArchTest
  static final ArchRule domain_is_independent =
      noClasses()
          .that()
          .resideInAPackage("com.vishop.iam..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.vishop.iam..application..",
              "com.vishop.iam..infrastructure..",
              "com.vishop.iam..presentation..",
              "org.springframework..",
              "jakarta..",
              "com.fasterxml.jackson..");

  @ArchTest
  static final ArchRule application_uses_ports =
      noClasses()
          .that()
          .resideInAPackage("com.vishop.iam..application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.vishop.iam..infrastructure..",
              "com.vishop.iam..presentation..",
              "org.springframework.security..",
              "org.springframework.jdbc..",
              "jakarta.persistence..",
              "io.jsonwebtoken..",
              "..common.security.jwt..");

  @ArchTest
  static final ArchRule controllers_use_cqrs_boundary =
      noClasses()
          .that()
          .resideInAPackage("com.vishop.iam..presentation..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.vishop.iam..application.service..",
              "com.vishop.iam..domain.ports..",
              "com.vishop.iam..domain.entities..",
              "com.vishop.iam..infrastructure.persistence..",
              "com.vishop.iam..infrastructure.messaging..");

  @ArchTest
  static final ArchRule shared_has_no_feature_dependencies =
      noClasses()
          .that()
          .resideInAPackage("com.vishop.iam.shared..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.vishop.iam.identity..", "com.vishop.iam.tenant..", "com.vishop.iam.access..");

  @ArchTest
  static final ArchRule identity_uses_public_feature_contracts =
      publicContractsOnly("identity", "tenant", "access");

  @ArchTest
  static final ArchRule tenant_uses_public_feature_contracts =
      publicContractsOnly("tenant", "identity", "access");

  @ArchTest
  static final ArchRule access_uses_public_feature_contracts =
      publicContractsOnly("access", "identity", "tenant");

  private static ArchRule publicContractsOnly(String source, String first, String second) {
    return noClasses()
        .that()
        .resideInAPackage("com.vishop.iam." + source + "..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "com.vishop.iam." + first + ".domain..",
            "com.vishop.iam." + first + ".infrastructure..",
            "com.vishop.iam." + first + ".presentation..",
            "com.vishop.iam." + first + ".application.port..",
            "com.vishop.iam." + first + ".application.service..",
            "com.vishop.iam." + first + ".application.command..",
            "com.vishop.iam." + first + ".application.query..",
            "com.vishop.iam." + second + ".domain..",
            "com.vishop.iam." + second + ".infrastructure..",
            "com.vishop.iam." + second + ".presentation..",
            "com.vishop.iam." + second + ".application.port..",
            "com.vishop.iam." + second + ".application.service..",
            "com.vishop.iam." + second + ".application.command..",
            "com.vishop.iam." + second + ".application.query..");
  }
}
