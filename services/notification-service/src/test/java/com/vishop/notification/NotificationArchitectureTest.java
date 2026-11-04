package com.vishop.notification;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class NotificationArchitectureTest {
  @Test
  void modulesOwnTheirInternalsAndOnlyExposeApplicationApis() {
    var classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.vishop.notification");
    var modules = java.util.Set.of("configuration", "inbox", "delivery", "announcement");
    assertThat(
            classes.stream()
                .anyMatch(
                    type ->
                        type.getPackageName()
                            .startsWith("com.vishop.notification.modules.catalog")))
        .isFalse();
    for (String module : modules) {
      String prefix = "com.vishop.notification.modules." + module + ".";
      assertThat(classes.stream().anyMatch(type -> type.getPackageName().startsWith(prefix)))
          .isTrue();
      for (var owner : classes) {
        if (!owner.getPackageName().startsWith(prefix)) continue;
        for (var dependency : owner.getDirectDependenciesFromSelf()) {
          String target = dependency.getTargetClass().getPackageName();
          if (target.startsWith("com.vishop.notification.modules.") && !target.startsWith(prefix))
            assertThat(target).as(dependency.getDescription()).contains(".application.api");
          assertThat(target)
              .as(dependency.getDescription())
              .doesNotStartWith("com.vishop.notification.composition.");
        }
      }
    }
    slices()
        .matching("com.vishop.notification.modules.(*)..")
        .should()
        .beFreeOfCycles()
        .check(classes);
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
            "..presentation..",
            "..composition..")
        .check(classes);
    noClasses()
        .that()
        .resideInAPackage("..application..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..infrastructure..",
            "..presentation..",
            "..composition..",
            "jakarta.persistence..",
            "org.springframework.data..",
            "org.apache.kafka..")
        .check(classes);
    noClasses()
        .that()
        .resideInAPackage("..presentation..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "com.vishop.notification.modules..domain..",
            "..infrastructure..",
            "..application.port..")
        .allowEmptyShould(true)
        .check(classes);
    noClasses()
        .that()
        .resideInAPackage("..shared..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "com.vishop.notification.modules..",
            "com.vishop.notification.composition..",
            "org.springframework..",
            "jakarta..")
        .check(classes);
    noClasses()
        .that()
        .resideInAPackage("..composition..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..domain..", "..infrastructure..", "..presentation..")
        .check(classes);
  }
}
