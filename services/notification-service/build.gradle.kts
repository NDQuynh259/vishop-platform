// Notification modules, setting HTTP/JPA adapters, and standalone migration CLI.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
}

dependencies {
    implementation(project(":libs:common-domain"))
    implementation(project(":libs:common-web"))
    implementation(project(":libs:common-security"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.security:spring-security-test")
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
