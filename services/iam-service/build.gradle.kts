plugins {
    id("org.springframework.boot")
}

springBoot {
    mainClass.set("com.vishop.iam.IamApplication")
}

tasks.jar { enabled = false }

dependencies {
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    implementation(project(":libs:common-domain"))
    implementation(project(":libs:common-web"))
    implementation(project(":libs:common-security"))
    implementation(project(":libs:common-events"))
    implementation(project(":libs:common-persistence"))
    implementation(project(":libs:common-observability"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("io.zonky.test:embedded-postgres:2.1.0")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    testImplementation(enforcedPlatform("io.zonky.test.postgres:embedded-postgres-binaries-bom:16.15.0"))
}

tasks.register<JavaExec>("migrateIamDb") {
    group = "database"
    description = "Migrate the explicitly configured IAM database using IAM_DB_URL/USER/PASSWORD."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.vishop.iam.infra.database.IamDatabaseMigration")
}
