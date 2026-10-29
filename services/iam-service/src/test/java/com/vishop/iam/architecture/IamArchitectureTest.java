package com.vishop.iam.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Table;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.hibernate.annotations.Immutable;
import org.springframework.data.jpa.repository.Query;

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
              "com.vishop.iam.modules.tenant..",
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
  static final ArchRule service_infrastructure_uses_only_module_contracts =
      noClasses()
          .that()
          .resideInAPackage("com.vishop.iam.infra..")
          .should()
          .dependOnClassesThat(moduleInternals("identity", "tenant"));

  @ArchTest
  static final ArchRule business_modules_have_no_dependency_cycles =
      slices().matching("com.vishop.iam.modules.(*)..").should().beFreeOfCycles();

  @ArchTest
  static final ArchRule composition_entity_mappings_are_read_only =
      classes()
          .that()
          .resideInAPackage("com.vishop.iam.composition..infrastructure.persistence.entities")
          .should()
          .beAnnotatedWith(Immutable.class)
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule service_infrastructure_does_not_use_composition =
      noClasses()
          .that()
          .resideInAPackage("com.vishop.iam.infra..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.vishop.iam.composition..");

  @ArchTest
  static void modules_map_and_query_only_owned_tables(JavaClasses classes) {
    Map<String, Set<String>> tables =
        Map.of(
            "identity", Set.of("users"),
            "tenant",
                Set.of(
                    "tenants",
                    "tenant_members",
                    "roles",
                    "role_permissions",
                    "tenant_invitations"));
    Set<String> iamTables =
        tables.values().stream().flatMap(Set::stream).collect(java.util.stream.Collectors.toSet());
    var tableReference = Pattern.compile("(?i)\\b(?:from|join|update|into)\\s+([a-z_][a-z_0-9]*)");
    for (JavaClass type : classes) {
      for (var module : tables.entrySet()) {
        if (!type.getPackageName().startsWith("com.vishop.iam.modules." + module.getKey() + "."))
          continue;
        if (type.isAnnotatedWith(Table.class)) {
          assertThat(module.getValue())
              .as("Tables owned by %s: %s", module.getKey(), type.getName())
              .contains(type.getAnnotationOfType(Table.class).name());
        }
        for (var method : type.getMethods()) {
          if (!method.isAnnotatedWith(Query.class)) continue;
          Query query = method.getAnnotationOfType(Query.class);
          if (!query.nativeQuery()) continue;
          var references = tableReference.matcher(query.value() + " " + query.countQuery());
          while (references.find()) {
            String table = references.group(1).toLowerCase(java.util.Locale.ROOT);
            if (iamTables.contains(table)) {
              assertThat(module.getValue())
                  .as("SQL table ownership: %s", method.getFullName())
                  .contains(table);
            }
          }
        }
      }
    }
  }

  @ArchTest
  static final ArchRule identity_uses_public_feature_contracts =
      publicContractsOnly("identity", "tenant");

  @ArchTest
  static final ArchRule tenant_uses_public_feature_contracts =
      publicContractsOnly("tenant", "identity");

  @ArchTest
  static final ArchRule access_is_merged_into_tenant =
      classes()
          .that()
          .resideInAPackage("com.vishop.iam.modules..")
          .should()
          .resideOutsideOfPackage("com.vishop.iam.modules.access..");

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
                  while (type.isArray()) type = type.getComponentType();
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
          .dependOnClassesThat(moduleInternals("identity", "tenant"));

  @ArchTest
  static final ArchRule composition_has_no_domain_models =
      classes()
          .that()
          .resideInAPackage("com.vishop.iam.composition..")
          .should()
          .resideOutsideOfPackage("com.vishop.iam.composition..domain..");

  private static ArchRule publicContractsOnly(String source, String... targets) {
    return noClasses()
        .that()
        .resideInAPackage("com.vishop.iam.modules." + source + "..")
        .should()
        .dependOnClassesThat(moduleInternals(targets));
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
    for (String module : new String[] {"identity", "tenant"}) {
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
        || name.equals("com.vishop.iam.modules.tenant.application.dto.MemberView")
        || name.equals("com.vishop.iam.modules.tenant.application.dto.TenantMemberResponseDto");
  }
}
