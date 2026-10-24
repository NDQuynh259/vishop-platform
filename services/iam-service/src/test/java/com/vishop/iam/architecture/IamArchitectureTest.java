package com.vishop.iam.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
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
              "org.springframework.web..",
              "jakarta.validation..",
              "jakarta.persistence..",
              "io.jsonwebtoken..",
              "..common.security.jwt..");

  @ArchTest
  static final ArchRule http_requests_belong_to_presentation =
      classes()
          .that()
          .haveSimpleNameEndingWith("Request")
          .should()
          .resideInAPackage("com.vishop.iam..presentation.rest.dto");

  @ArchTest
  static final ArchRule controllers_use_cqrs_boundary =
      noClasses()
          .that()
          .resideInAPackage("com.vishop.iam..presentation..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.vishop.iam..application.service..",
              "com.vishop.iam..domain.port..",
              "com.vishop.iam..domain.entity..",
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
              "com.vishop.iam.modules.identity..",
              "com.vishop.iam.modules.tenant..",
              "com.vishop.iam.modules.access..",
              "com.vishop.iam.contracts..",
              "com.vishop.iam.composition..");

  @ArchTest
  static final ArchRule identity_uses_public_feature_contracts =
      publicContractsOnly("identity", "tenant", "access");

  @ArchTest
  static final ArchRule tenant_uses_public_feature_contracts =
      publicContractsOnly("tenant", "identity", "access");

  @ArchTest
  static final ArchRule access_uses_public_feature_contracts =
      publicContractsOnly("access", "identity", "tenant");

  @ArchTest
  static final ArchRule contracts_are_independent =
      noClasses()
          .that()
          .resideInAPackage("com.vishop.iam.contracts..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.vishop.iam.modules..",
              "com.vishop.iam..domain..",
              "com.vishop.iam..infrastructure..",
              "com.vishop.iam..presentation..",
              "com.vishop.iam.composition..",
              "org.springframework..",
              "jakarta..");

  @ArchTest
  static final ArchRule modules_do_not_depend_on_composition =
      noClasses()
          .that()
          .resideInAPackage("com.vishop.iam.modules..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.vishop.iam.composition..");

  @ArchTest
  static final ArchRule composition_uses_only_module_contracts =
      noClasses()
          .that()
          .resideInAPackage("com.vishop.iam.composition..")
          .should()
          .dependOnClassesThat(moduleInternals("identity", "tenant", "access"));

  private static ArchRule publicContractsOnly(String source, String first, String second) {
    return noClasses()
        .that()
        .resideInAPackage("com.vishop.iam.modules." + source + "..")
        .should()
        .dependOnClassesThat(moduleInternals(first, second));
  }

  private static DescribedPredicate<JavaClass> moduleInternals(String... modules) {
    return new DescribedPredicate<>("be internal to modules " + String.join(", ", modules)) {
      @Override
      public boolean test(JavaClass type) {
        String name = type.getPackageName();
        for (String module : modules) {
          String prefix = "com.vishop.iam.modules." + module;
          if (name.equals(prefix) || name.startsWith(prefix + ".")) return true;
        }
        return false;
      }
    };
  }
}
