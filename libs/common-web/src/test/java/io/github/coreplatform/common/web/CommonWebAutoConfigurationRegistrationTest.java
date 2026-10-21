package io.github.coreplatform.common.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommonWebAutoConfigurationRegistrationTest {

    // #region importsFileNamesExistingAutoConfigurationClasses
    @Test
    void importsFileNamesExistingAutoConfigurationClasses() throws IOException {
        String importsPath = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";
        List<String> imports;
        try (var input = getClass().getClassLoader().getResourceAsStream(importsPath)) {
            assertNotNull(input, "Auto-configuration imports file must exist");
            imports = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .filter(line -> !line.isBlank())
                    .toList();
        }

        assertTrue(imports.contains("io.github.coreplatform.common.web.config.CommonWebAutoConfiguration"));
        imports.forEach(className -> {
            try {
                Class<?> configurationClass = Class.forName(className);
                assertTrue(configurationClass.isAnnotationPresent(AutoConfiguration.class));
            } catch (ClassNotFoundException exception) {
                throw new AssertionError("Imported auto-configuration class must exist: " + className, exception);
            }
        });
    }
    // #endregion
}
