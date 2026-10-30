import org.springframework.boot.gradle.tasks.run.BootRun

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
    testImplementation(project(":libs:common-test"))
    implementation(project(":libs:common-web"))
    implementation(project(":libs:common-security"))
    implementation(project(":libs:common-events"))
    implementation(project(":libs:common-persistence"))
    implementation(project(":libs:common-observability"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
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

apply(from = rootProject.file("gradle/jvm-tools.gradle.kts"))

// Service commands, equivalent to per-service package.json scripts.
mapOf("dev" to "dev", "start" to "prod").forEach { (command, profile) ->
    tasks.register<BootRun>(command) {
        group = "application"
        description = "Run this service with the $profile profile."
        mainClass.set(springBoot.mainClass)
        classpath = sourceSets["main"].runtimeClasspath
        systemProperty("spring.profiles.active", profile)
    }
}

tasks.register("migrate") {
    group = "database"
    description = "Run this service's database migrations without starting the server."
    dependsOn("migrateIamDb")
}

tasks.register("commands") {
    group = "help"
    description = "List commands for this service, similar to npm run."
    doLast {
        val prefix = ":services:iam-service:"
        val commands = linkedMapOf(
            "commands" to "List service commands",
            "tasks --all" to "List every Gradle task",
            "dev" to "Run with the dev profile",
            "start" to "Run with the prod profile",
            "bootRun" to "Run from source with Spring Boot's default configuration",
            "runJar" to "Build and run the boot JAR; optional -Pprofile=dev (default prod)",
            "classes" to "Compile code and process resources",
            "compileJava" to "Compile Java sources",
            "test" to "Run service tests; optional --tests <pattern>",
            "check" to "Run tests and verification checks",
            "build" to "Build and check this service",
            "bootJar" to "Package the executable JAR",
            "clean" to "Remove this service's build outputs",
            "spotlessApply" to "Format Java sources",
            "spotlessCheck" to "Check Java formatting",
            "dependencies" to "Show dependency trees",
            "dependencyInsight --dependency <name> --configuration runtimeClasspath" to "Explain a dependency version",
            "javaVersion" to "Show Java 21 toolchain version",
            "javaProcesses" to "List local JVM process IDs",
            "threadDump -Ppid=<PID>" to "Print a target JVM's thread dump",
            "heapInfo -Ppid=<PID>" to "Show target JVM heap information",
        )
        commands["migrate"] = "Run database migrations (explicit database environment required)"
        commands.forEach { (command, help) ->
            println("  .\\gradlew.bat $prefix${command.padEnd(15)} $help")
        }
    }
}
