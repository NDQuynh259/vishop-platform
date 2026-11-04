package com.vishop.notification.infra.database;

import org.flywaydb.core.Flyway;

/** Runs notification migrations independently of the application runtime. */
public final class NotificationDatabaseMigration {
  private NotificationDatabaseMigration() {}

  public static void main(String[] args) {
    String url = requiredEnvironment("NOTIFICATION_DB_URL");
    if (!url.startsWith("jdbc:postgresql:")) {
      throw new IllegalArgumentException("NOTIFICATION_DB_URL must be a PostgreSQL JDBC URL");
    }
    var result =
        Flyway.configure()
            .dataSource(
                url,
                requiredEnvironment("NOTIFICATION_DB_USER"),
                requiredEnvironment("NOTIFICATION_DB_PASSWORD"))
            .locations("classpath:db/migration")
            .baselineOnMigrate(false)
            .cleanDisabled(true)
            .load()
            .migrate();
    System.out.println("Notification migrations applied: " + result.migrationsExecuted);
  }

  private static String requiredEnvironment(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Set " + name + " before running notification migrations");
    }
    return value;
  }
}
