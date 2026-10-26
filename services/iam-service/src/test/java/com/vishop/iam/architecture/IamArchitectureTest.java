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
              "com.vishop.iam.infra..",
              "com.vishop.iam..presentation..",
              "io.github.coreplatform.common.cache..",
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
              "com.vishop.iam.infra..",
              "com.vishop.iam..presentation..",
              "org.springframework.security..",
              "org.springframework.jdbc..",
              "org.springframework.data.redis..",
              "io.github.coreplatform.common.cache..",
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
              "com.vishop.iam..infrastructure.messaging..",
              "com.vishop.iam.infra.persistence..",
              "com.vishop.iam.infra.messaging..",
              "com.vishop.iam.infra.database..");

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
              "com.vishop.iam.composition..");

  @ArchTest
  static final ArchRule shared_is_framework_independent =
      noClasses()
          .that()
          .resideInAPackage("com.vishop.iam.shared..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.vishop.iam.infra..",
              "com.vishop.iam..infrastructure..",
              "org.springframework..",
              "jakarta..",
              "com.fasterxml.jackson..",
              "java.sql..",
              "javax.sql..",
              "org.flywaydb..",
              "org.apache.kafka..",
              "io.github.coreplatform.common.cache..",
              "io.jsonwebtoken..");

  @ArchTest
  static final ArchRule common_infrastructure_uses_no_module_internals =
      noClasses()
          .that()
          .resideInAPackage("com.vishop.iam.infra..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.vishop.iam.modules..", "com.vishop.iam.composition..");

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
  static final ArchRule module_api_implementations_are_not_public =
      classes()
          .that()
          .resideInAPackage("com.vishop.iam.modules..application.api")
          .and()
          .haveSimpleNameEndingWith("ApplicationApi")
          .should()
          .bePackagePrivate()
          .andShould()
          .beAnnotatedWith(org.springframework.stereotype.Component.class);

  @ArchTest
  static final ArchRule module_api_contracts_are_public_interfaces =
      classes()
          .that()
          .resideInAPackage("com.vishop.iam.modules..application.api")
          .and()
          .haveSimpleNameEndingWith("Api")
          .and()
          .haveSimpleNameNotEndingWith("ApplicationApi")
          .should()
          .bePublic()
          .andShould()
          .beInterfaces();

  @ArchTest
  static final ArchRule public_api_types_are_independent =
      classes()
          .that(publicApiTypes())
          .should()
          .onlyDependOnClassesThat(
              new DescribedPredicate<JavaClass>("be JDK, shared, or public module API/DTO types") {
                @Override
                public boolean test(JavaClass type) {
                  return type.isPrimitive()
                      || type.getPackageName().startsWith("java.")
                      || type.getPackageName().startsWith("com.vishop.iam.shared.")
                      || isPublicApiType(type);
                }
              });

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

  @ArchTest
  static final ArchRule composition_has_no_domain_models =
      classes()
          .that()
          .resideInAPackage("com.vishop.iam.composition..")
          .should()
          .resideOutsideOfPackage("com.vishop.iam.composition..domain..");

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
          if ((name.equals(prefix) || name.startsWith(prefix + ".")) && !isPublicApiType(type))
            return true;
        }
        return false;
      }
    };
  }

  private static DescribedPredicate<JavaClass> publicApiTypes() {
    return new DescribedPredicate<JavaClass>("be a public module API or DTO") {
      @Override
      public boolean test(JavaClass type) {
        return isPublicApiType(type);
      }
    };
  }

  private static boolean isPublicApiType(JavaClass type) {
    String name = type.getName();
    for (String module : new String[] {"identity", "tenant", "access"}) {
      String api =
          "com.vishop.iam.modules."
              + module
              + ".application.api."
              + Character.toUpperCase(module.charAt(0))
              + module.substring(1)
              + "Api";
      if (name.equals(api) || name.startsWith(api + "$")) return true;
    }
    return name.equals("com.vishop.iam.modules.identity.application.dto.UserView")
        || name.equals("com.vishop.iam.modules.tenant.application.dto.TenantResponseDto")
        || name.equals("com.vishop.iam.modules.access.application.dto.TenantMemberResponseDto");
  }
}
