package com.vishop.audit.infra.database;

import org.flywaydb.core.Flyway;

/** Runs audit migrations without starting Spring, HTTP, JPA or Kafka. */
public final class AuditDatabaseMigration {
  private AuditDatabaseMigration() {}

  public static void main(String[] args) {
    String url = requiredEnvironment("AUDIT_DB_URL");
    if (!url.startsWith("jdbc:postgresql:")) {
      throw new IllegalArgumentException("AUDIT_DB_URL must be a PostgreSQL JDBC URL");
    }
    var flyway =
        Flyway.configure()
            .dataSource(
                url, requiredEnvironment("AUDIT_DB_USER"), requiredEnvironment("AUDIT_DB_PASSWORD"))
            .locations("classpath:db/migration")
            .baselineOnMigrate(false)
            .cleanDisabled(true)
            .load();
    var result = flyway.migrate();
    System.out.println("Audit migrations applied: " + result.migrationsExecuted);
  }

  private static String requiredEnvironment(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Set " + name + " before running audit migrations");
    }
    return value;
  }
}
