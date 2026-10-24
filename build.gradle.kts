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
