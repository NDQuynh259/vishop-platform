package com.vishop.iam.shared.infrastructure.database;

import org.flywaydb.core.Flyway;

/** Explicit database preparation tool; this module does not yet start an HTTP service. */
public final class IamDatabaseMigration {
  private IamDatabaseMigration() {}

  public static void main(String[] args) {
    String url = requiredEnvironment("IAM_DB_URL");
    if (!url.startsWith("jdbc:postgresql:")) {
      throw new IllegalArgumentException("IAM_DB_URL must be a PostgreSQL JDBC URL");
    }
    Flyway flyway =
        Flyway.configure()
            .dataSource(
                url, requiredEnvironment("IAM_DB_USER"), requiredEnvironment("IAM_DB_PASSWORD"))
            .locations("classpath:db/migration")
            .baselineOnMigrate(false)
            .cleanDisabled(true)
            .load();
    var result = flyway.migrate();
    System.out.println("IAM migrations applied: " + result.migrationsExecuted);
  }

  private static String requiredEnvironment(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Set " + name + " before running IAM migrations");
    }
    return value;
  }
}
