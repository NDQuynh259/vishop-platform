plugins {
    id("org.springframework.boot")
}

springBoot {
    mainClass.set("com.vishop.audit.AuditApplication")
}

dependencies {
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    implementation(project(":libs:common-web"))
    implementation(project(":libs:common-security"))
    implementation(project(":libs:common-events"))
    implementation(project(":libs:common-persistence"))
    implementation(project(":libs:common-observability"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    testImplementation("com.tngtech.archunit:archunit:1.3.0")
    testImplementation("io.zonky.test:embedded-postgres:2.1.0")
    testImplementation(enforcedPlatform("io.zonky.test.postgres:embedded-postgres-binaries-bom:16.15.0"))
}

tasks.register<JavaExec>("dbMigrate") {
    group = "database"
    description = "Migrate the explicitly configured audit database using AUDIT_DB_URL/USER/PASSWORD."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.vishop.audit.infra.database.AuditDatabaseMigration")
}

tasks.test {
    systemProperty("auditMigrationClasspath", sourceSets["main"].runtimeClasspath.asPath)
}
