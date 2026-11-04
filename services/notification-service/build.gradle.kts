// Domain/application modules and migration CLI; runtime adapters are added separately.
dependencies {
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    testImplementation("io.zonky.test:embedded-postgres:2.1.0")
    testImplementation("com.tngtech.archunit:archunit:1.3.0")
    testImplementation(enforcedPlatform("io.zonky.test.postgres:embedded-postgres-binaries-bom:16.15.0"))
}

tasks.register<JavaExec>("dbMigrate") {
    group = "database"
    description = "Migrate the notification database using NOTIFICATION_DB_URL/USER/PASSWORD."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.vishop.notification.infra.database.NotificationDatabaseMigration")
}

tasks.register("migrate") {
    group = "database"
    description = "Run database migrations without starting a server."
    dependsOn("dbMigrate")
}

tasks.test {
    systemProperty("notificationMigrationClasspath", sourceSets["main"].runtimeClasspath.asPath)
}
