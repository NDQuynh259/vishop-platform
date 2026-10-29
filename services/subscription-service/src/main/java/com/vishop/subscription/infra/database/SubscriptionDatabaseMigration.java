package com.vishop.subscription.infra.database;

import org.flywaydb.core.Flyway;

/** Runs subscription migrations without starting Spring, HTTP, JPA or Kafka. */
public final class SubscriptionDatabaseMigration {
  private SubscriptionDatabaseMigration() {}

  public static void main(String[] args) {
    String url = requiredEnvironment("SUBSCRIPTION_DB_URL", "BILLING_DB_URL");
    if (!url.startsWith("jdbc:postgresql:")) {
      throw new IllegalArgumentException("SUBSCRIPTION_DB_URL must be a PostgreSQL JDBC URL");
    }
    var flyway =
        Flyway.configure()
            .dataSource(
                url,
                requiredEnvironment("SUBSCRIPTION_DB_USER", "BILLING_DB_USER"),
                requiredEnvironment("SUBSCRIPTION_DB_PASSWORD", "BILLING_DB_PASSWORD"))
            .locations("classpath:db/migration")
            .baselineOnMigrate(false)
            .cleanDisabled(true)
            .load();
    var result = flyway.migrate();
    System.out.println("Subscription migrations applied: " + result.migrationsExecuted);
  }

  private static String requiredEnvironment(String name, String legacyName) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) value = System.getenv(legacyName);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Set " + name + " before running subscription migrations");
    }
    return value;
  }
}
