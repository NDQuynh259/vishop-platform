plugins {
    java
    id("org.springframework.boot") version "3.3.5" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
    id("com.diffplug.spotless") version "6.25.0"
    eclipse
}

dependencies {
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:3.3.5"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.tngtech.archunit:archunit:1.3.0")
    testImplementation("org.yaml:snakeyaml")
}

spotless {
    java {
        target("src/test/**/*.java")
        googleJavaFormat("1.22.0")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

allprojects {
    group = "io.github.coreplatform"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "eclipse")

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            target("src/**/*.java")
            googleJavaFormat("1.22.0")
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
        }
    }

    configure<org.gradle.plugins.ide.eclipse.model.EclipseModel> {
        classpath {
            plusConfigurations.add(configurations.getByName("compileClasspath"))
            file.whenMerged {
                val cp = this as org.gradle.plugins.ide.eclipse.model.Classpath
                val mainCompileFiles = configurations.findByName("compileClasspath")?.files ?: emptySet()
                cp.entries.filterIsInstance<org.gradle.plugins.ide.eclipse.model.Library>().forEach { entry ->
                    entry.isExported = true
                    if (File(entry.path) in mainCompileFiles) {
                        entry.entryAttributes["gradle_used_by_scope"] = "main,test"
                    }
                }
            }
        }
    }

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    configure<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension> {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.5")
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:2023.0.3")
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    dependencies {
        "api"("org.slf4j:slf4j-api")
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
    }
}

tasks.test {
    useJUnitPlatform()
    dependsOn(subprojects.map { it.tasks.named("classes") })
    systemProperty("workspaceRoot", rootDir.absolutePath)
    inputs.files(subprojects.map { it.layout.buildDirectory.dir("classes/java/main") })
    inputs.files(subprojects.flatMap {
        it.fileTree("src/main/resources") {
            include("application.yml", "db/migration/*.sql")
        }.files
    })
}

apply(from = rootProject.file("gradle/jvm-tools.gradle.kts"))

// Project commands: the Gradle equivalent of package.json scripts.
fun projectTaskPaths(task: String) = allprojects.map {
    if (it == rootProject) ":$task" else "${it.path}:$task"
}

val projectCommands = linkedMapOf(
    "runIam" to ("Start IAM service (:8080)." to listOf(":services:iam-service:dev")),
    "runGateway" to ("Start API gateway (:8000)." to listOf(":services:api-gateway:dev")),
    "runAudit" to ("Start audit service (:8082)." to listOf(":services:audit-service:dev")),
    "migrateIam" to ("Run IAM migrations; requires IAM_DB_URL/USER/PASSWORD." to listOf(":services:iam-service:migrate")),
    "migrateAudit" to ("Run audit migrations; requires AUDIT_DB_URL/USER/PASSWORD." to listOf(":services:audit-service:migrate")),
    "packageAll" to ("Package all three service boot JARs." to listOf(
        ":services:iam-service:bootJar",
        ":services:api-gateway:bootJar",
        ":services:audit-service:bootJar",
    )),
    "buildAll" to ("Build and check every project." to projectTaskPaths("build")),
    "compileAll" to ("Compile Java sources across all projects." to projectTaskPaths("compileJava")),
    "classesAll" to ("Compile and process resources across all projects." to projectTaskPaths("classes")),
    "dependenciesAll" to ("Show dependency trees across all projects." to projectTaskPaths("dependencies")),
    "testAll" to ("Run all module tests and root architecture tests." to projectTaskPaths("test")),
    "checkAll" to ("Run all project checks." to projectTaskPaths("check")),
    "formatAll" to ("Format Java sources in every project." to projectTaskPaths("spotlessApply")),
    "lintAll" to ("Check Java formatting without changing files." to projectTaskPaths("spotlessCheck")),
    "cleanAll" to ("Remove Gradle build outputs in every project." to projectTaskPaths("clean")),
)

projectCommands.forEach { (name, command) ->
    tasks.register(name) {
        group = "project"
        description = command.first
        dependsOn(command.second)
    }
}

tasks.register("commands") {
    group = "project"
    description = "List project commands, similar to npm run."
    doLast {
        println("Project commands (run from the repository root):")
        projectCommands.forEach { (name, command) ->
            println("  .\\gradlew.bat ${name.padEnd(15)} ${command.first}")
        }
        println("JDK commands:")
        println("  .\\gradlew.bat :javaVersion")
        println("  .\\gradlew.bat :javaProcesses")
        println("  .\\gradlew.bat :threadDump -Ppid=<PID>")
        println("  .\\gradlew.bat :heapInfo -Ppid=<PID>")
        println("  .\\gradlew.bat :runJar -Pjar=<path> [-Pprofile=prod]")
        println("Full Gradle task list: .\\gradlew.bat tasks --all")
        println("Service command lists: .\\gradlew.bat :services:<service-name>:commands")
    }
}
