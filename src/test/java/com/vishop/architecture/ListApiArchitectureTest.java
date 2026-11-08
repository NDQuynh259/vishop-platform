package com.vishop.architecture;

import static org.junit.jupiter.api.Assertions.*;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ListApiArchitectureTest {
  @Test
  void listEndpointsUseSharedEnvelopeAndPaginationRequest() throws Exception {
    var classpaths = new ArrayList<Path>();
    Path services = Path.of(System.getProperty("workspaceRoot"), "services");
    try (var projects = Files.list(services)) {
      projects
          .map(path -> path.resolve("build/classes/java/main"))
          .filter(Files::isDirectory)
          .forEach(classpaths::add);
    }
    var classes = new ClassFileImporter().importPaths(classpaths);
    var rawCollections =
        Set.of(
            "java.util.List",
            "java.util.Set",
            "java.util.Collection",
            "java.lang.Iterable",
            "org.springframework.data.domain.Page",
            "io.github.coreplatform.common.domain.pagination.PagedResult");
    int checked = 0;
    for (var controller : classes) {
      if (!controller.isAnnotatedWith("org.springframework.web.bind.annotation.RestController"))
        continue;
      for (var method : controller.getMethods()) {
        if (!method.isAnnotatedWith("org.springframework.web.bind.annotation.GetMapping")) continue;
        String result = method.getRawReturnType().getName();
        assertFalse(
            rawCollections.contains(result), method.getFullName() + " must return ListEnvelope");
        if (result.equals("io.github.coreplatform.common.web.pagination.ListEnvelope")) {
          checked++;
          assertTrue(
              method.getRawParameterTypes().stream()
                  .anyMatch(
                      type ->
                          type.getName()
                              .equals(
                                  "io.github.coreplatform.common.web.pagination.PaginationRequest")),
              method.getFullName() + " must accept PaginationRequest");
        }
      }
    }
    assertTrue(checked > 0, "Compile service controllers before checking the list API contract");
  }
}
