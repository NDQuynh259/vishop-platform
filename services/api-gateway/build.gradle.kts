import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    id("org.springframework.boot")
}

springBoot {
    mainClass.set("com.vishop.gateway.GatewayApplication")
}

dependencies {
    implementation(project(":libs:common-domain"))
    testImplementation(project(":libs:common-test"))
    implementation(project(":libs:common-cache"))
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
    implementation(project(":libs:common-security"))
    implementation(project(":libs:common-observability"))
    implementation("org.springframework.cloud:spring-cloud-starter-gateway")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springdoc:springdoc-openapi-starter-webflux-api:2.6.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("com.github.codemonstur:embedded-redis:1.4.3")
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

tasks.register("commands") {
    group = "help"
    description = "List commands for this service, similar to npm run."
    doLast {
        val prefix = ":services:api-gateway:"
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
        commands.forEach { (command, help) ->
            println("  .\\gradlew.bat $prefix${command.padEnd(15)} $help")
        }
    }
}
