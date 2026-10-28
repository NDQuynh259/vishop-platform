package com.vishop.audit;

import static org.assertj.core.api.Assertions.assertThat;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuditDatabaseMigrationTest {
  @TempDir Path directory;

  @Test
  void migratesDatabaseWithoutStartingServiceAndCanRunAgain() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().setPort(0).start()) {
      var environment =
          Map.of(
              "AUDIT_DB_URL",
              postgres.getJdbcUrl("postgres", "postgres"),
              "AUDIT_DB_USER",
              "postgres",
              "AUDIT_DB_PASSWORD",
              "postgres");
      var first = run(environment);
      assertThat(first.exitCode()).isZero();
      assertThat(first.output())
          .contains("Audit migrations applied: 2")
          .doesNotContain("Started AuditApplication");
      var second = run(environment);
      assertThat(second.exitCode()).isZero();
      assertThat(second.output()).contains("Audit migrations applied: 0");
    }
  }

  @Test
  void requiresExplicitDatabaseAndRejectsNonPostgresUrls() throws Exception {
    var missing = run(Map.of());
    assertThat(missing.exitCode()).isNotZero();
    assertThat(missing.output()).contains("Set AUDIT_DB_URL");
    var invalid = run(Map.of("AUDIT_DB_URL", "jdbc:h2:mem:test"));
    assertThat(invalid.exitCode()).isNotZero();
    assertThat(invalid.output()).contains("must be a PostgreSQL JDBC URL");
  }

  private Result run(Map<String, String> environment) throws Exception {
    String invocation = UUID.randomUUID().toString();
    Path arguments = directory.resolve(invocation + ".args");
    String classpath =
        System.getProperty("auditMigrationClasspath").replace("\\", "\\\\").replace("\"", "\\\"");
    Files.writeString(
        arguments,
        "-cp\n\"" + classpath + "\"\ncom.vishop.audit.infra.database.AuditDatabaseMigration\n");
    Path output = directory.resolve(invocation + ".log");
    String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
    var builder =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "@" + arguments)
            .redirectErrorStream(true)
            .redirectOutput(output.toFile());
    for (String name : new String[] {"AUDIT_DB_URL", "AUDIT_DB_USER", "AUDIT_DB_PASSWORD"})
      builder.environment().remove(name);
    builder.environment().putAll(environment);
    var process = builder.start();
    try {
      assertThat(process.waitFor(30, TimeUnit.SECONDS)).as("Migration CLI must terminate").isTrue();
      return new Result(process.exitValue(), Files.readString(output));
    } finally {
      if (process.isAlive()) process.destroyForcibly();
    }
  }

  private record Result(int exitCode, String output) {}
}
