package io.github.coreplatform.common.observability;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;

class ObservabilityAutoConfigurationRegistrationTest {

  // #region importsFileNamesExistingAutoConfigurationClasses
  @Test
  void importsFileNamesExistingAutoConfigurationClasses() throws IOException {
    String importsPath =
        "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";
    List<String> imports;
    try (var input = getClass().getClassLoader().getResourceAsStream(importsPath)) {
      assertTrue(input != null, "Auto-configuration imports file must exist");
      imports =
          new String(input.readAllBytes(), StandardCharsets.UTF_8)
              .lines()
              .filter(line -> !line.isBlank())
              .toList();
    }

    assertTrue(
        imports.contains(
            "io.github.coreplatform.common.observability.config.ObservabilityAutoConfiguration"));
    assertTrue(
        imports.contains(
            "io.github.coreplatform.common.observability.config.MetricsAutoConfiguration"));
    imports.forEach(
        className -> {
          try {
            Class<?> configurationClass = Class.forName(className);
            assertTrue(configurationClass.isAnnotationPresent(AutoConfiguration.class));
          } catch (ClassNotFoundException exception) {
            throw new AssertionError(
                "Imported auto-configuration class must exist: " + className, exception);
          }
        });
  }
  // #endregion
}
