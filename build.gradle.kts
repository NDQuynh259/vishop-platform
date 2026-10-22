plugins {
    java
    id("org.springframework.boot") version "3.3.5" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
    eclipse
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
    apply(plugin = "eclipse")

    configure<org.gradle.plugins.ide.eclipse.model.EclipseModel> {
        classpath {
            plusConfigurations.add(configurations.getByName("compileClasspath"))
            file.whenMerged {
                val cp = this as org.gradle.plugins.ide.eclipse.model.Classpath
                val mainCompileFiles = configurations.findByName("compileClasspath")?.files ?: emptySet()
                cp.entries.filterIsInstance<org.gradle.plugins.ide.eclipse.model.Library>().forEach { entry ->
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
