package com.vishop.subscription.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.*;
import jakarta.persistence.Table;
import java.util.*;
import org.junit.jupiter.api.Test;

class SubscriptionArchitectureTest {
  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("com.vishop.subscription");

  @Test
  void serviceOwnsItsDomainAndUsesPorts() {
    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("com.vishop.iam..", "com.vishop.audit..", "com.vishop.billing..")
        .check(CLASSES);
    noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework..",
            "jakarta..",
            "..application..",
            "..infrastructure..",
            "..presentation..")
        .check(CLASSES);
    noClasses()
        .that()
        .resideInAPackage("..application..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "jakarta.persistence..",
            "..infrastructure..",
            "..presentation..",
            "org.springframework.security..")
        .check(CLASSES);
    noClasses()
        .that()
        .resideInAPackage("..presentation..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..infrastructure..", "..domain.port..", "..domain.entity..")
        .check(CLASSES);
  }

  @Test
  void modulesDoNotDependDirectlyOnEachOtherOrComposition() {
    noClasses()
        .that()
        .resideInAPackage("com.vishop.subscription.modules.subscription..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "com.vishop.subscription.modules.payment..", "com.vishop.subscription.composition..")
        .check(CLASSES);
    noClasses()
        .that()
        .resideInAPackage("com.vishop.subscription.modules.payment..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "com.vishop.subscription.modules.subscription..",
            "com.vishop.subscription.composition..")
        .check(CLASSES);
    noClasses()
        .that()
        .resideInAPackage("com.vishop.subscription.shared..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "com.vishop.subscription.modules..",
            "com.vishop.subscription.composition..",
            "com.vishop.subscription.infra..")
        .check(CLASSES);
  }

  @Test
  void compositionUsesOnlyExportedContracts() {
    for (var type : CLASSES) {
      if (!type.getPackageName().startsWith("com.vishop.subscription.composition.")) continue;
      for (var dependency : type.getDirectDependenciesFromSelf()) {
        var target = dependency.getTargetClass();
        String pkg = target.getPackageName();
        if (!pkg.startsWith("com.vishop.subscription.modules.")) continue;
        assertThat(pkg).as(dependency.getDescription()).contains(".application.api");
      }
    }
  }

  @Test
  void persistenceTablesBelongToTheirBusinessModule() {
    Map<String, String> owners =
        Map.of(
            "subscription_plans",
            "subscription",
            "payment_allocations",
            "payment",
            "subscription_prices",
            "subscription",
            "subscriptions",
            "subscription",
            "subscription_orders",
            "subscription",
            "subscription_terms",
            "subscription",
            "sepay_webhook_inbox",
            "payment",
            "payment_transactions",
            "payment");
    Set<String> seen = new HashSet<>();
    for (var type : CLASSES) {
      if (!type.isAnnotatedWith(Table.class)) continue;
      String table = type.getAnnotationOfType(Table.class).name();
      assertThat(owners).containsKey(table);
      assertThat(type.getPackageName())
          .contains(".modules." + owners.get(table) + ".infrastructure.persistence.entities");
      assertThat(seen.add(table)).as("Single entity mapping for %s", table).isTrue();
    }
    assertThat(seen).containsExactlyInAnyOrderElementsOf(owners.keySet());
  }
}
