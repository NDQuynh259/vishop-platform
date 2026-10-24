package com.vishop.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.*;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** Platform-wide persistence rules, including services and shared production libraries. */
class PersistenceArchitectureTest {
  private static final Path WORKSPACE = Path.of(System.getProperty("workspaceRoot"));

  @Test
  void productionCodeUsesJpaInsteadOfDirectJdbc() throws IOException {
    var classpaths = new ArrayList<Path>();
    for (String group : new String[] {"services", "libs"}) {
      try (var projects = Files.list(WORKSPACE.resolve(group))) {
        projects
            .map(path -> path.resolve("build/classes/java/main"))
            .filter(Files::isDirectory)
            .forEach(classpaths::add);
      }
    }
    assertFalse(
        classpaths.isEmpty(), "Compile all production modules before checking architecture");
    var classes = new ClassFileImporter().importPaths(classpaths);
    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.springframework.jdbc..", "java.sql..", "javax.sql..", "org.jooq..")
        .because("runtime persistence must use Spring Data JPA or EntityManager")
        .check(classes);
  }

  @Test
  void databaseServicesValidateEntitiesAndMigrateOnlyWithFlywaySql() throws IOException {
    int checked = 0;
    try (var services = Files.list(WORKSPACE.resolve("services"))) {
      for (var service : services.filter(Files::isDirectory).toList()) {
        var resources = service.resolve("src/main/resources");
        var config = resources.resolve("application.yml");
        if (!Files.exists(config)) continue;
        Map<String, Object> document;
        try (var reader = Files.newBufferedReader(config)) {
          document = new Yaml().load(reader);
        }
        if (value(document, "spring", "datasource") == null) continue;
        String name = service.getFileName().toString();
        assertEquals("validate", value(document, "spring", "jpa", "hibernate", "ddl-auto"), name);
        assertEquals(false, value(document, "spring", "jpa", "generate-ddl"), name);
        assertEquals("never", value(document, "spring", "sql", "init", "mode"), name);
        assertEquals(true, value(document, "spring", "flyway", "enabled"), name);
        assertEquals(false, value(document, "spring", "flyway", "baseline-on-migrate"), name);
        assertEquals(true, value(document, "spring", "flyway", "clean-disabled"), name);
        try (var files = Files.list(resources.resolve("db/migration"))) {
          assertTrue(
              files.anyMatch(path -> path.getFileName().toString().matches("V.+__.+\\.sql")), name);
        }
        assertFalse(Files.exists(resources.resolve("schema.sql")), name);
        checked++;
      }
    }
    assertTrue(checked > 0, "Expected at least one database-backed service");
  }

  private static Object value(Map<String, Object> document, String... keys) {
    Object current = document;
    for (String key : keys) {
      if (!(current instanceof Map<?, ?> map)) return null;
      current = map.get(key);
    }
    return current;
  }
}
