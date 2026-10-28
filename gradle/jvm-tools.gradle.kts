import org.gradle.api.tasks.bundling.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService

// Shared JDK commands, available from the root and each service's build.gradle.kts.
val jdkTools = extensions.getByType<JavaToolchainService>().compilerFor {
    languageVersion.set(JavaLanguageVersion.of(21))
}
val executableSuffix = if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""

fun jdkExecutable(name: String) = jdkTools.get().metadata.installationPath
    .file("bin/$name$executableSuffix").asFile.absolutePath

fun requiredJvmPid(): String {
    val value = providers.gradleProperty("pid").orNull
    require(value?.toLongOrNull()?.let { it > 0 } == true) {
        "Provide the target JVM process ID with -Ppid=<PID>. Use javaProcesses to list JVMs."
    }
    return value!!
}

tasks.register<Exec>("javaVersion") {
    group = "jvm"
    description = "Show the Java 21 toolchain version."
    doFirst { commandLine(jdkExecutable("java"), "-version") }
}

tasks.register<Exec>("javaProcesses") {
    group = "jvm"
    description = "List local JVM processes using jps -l."
    doFirst { commandLine(jdkExecutable("jps"), "-l") }
}

tasks.register<Exec>("threadDump") {
    group = "jvm"
    description = "Print the target JVM's thread dump; requires -Ppid=<PID>."
    doFirst { commandLine(jdkExecutable("jstack"), requiredJvmPid()) }
}

tasks.register<Exec>("heapInfo") {
    group = "jvm"
    description = "Show target JVM heap information; requires -Ppid=<PID>."
    doFirst { commandLine(jdkExecutable("jcmd"), requiredJvmPid(), "GC.heap_info") }
}

val explicitJar = providers.gradleProperty("jar")
tasks.register<Exec>("runJar") {
    group = "application"
    description = "Run an executable JAR; service defaults to its bootJar, root requires -Pjar=<path>."
    if (!explicitJar.isPresent && plugins.hasPlugin("org.springframework.boot")) {
        dependsOn("bootJar")
    }
    doFirst {
        val archive = if (explicitJar.isPresent) {
            file(explicitJar.get())
        } else {
            require(plugins.hasPlugin("org.springframework.boot")) {
                "Provide -Pjar=<path> at the root, or run a service's runJar task."
            }
            tasks.named<Jar>("bootJar").get().archiveFile.get().asFile
        }
        require(archive.isFile) { "Executable JAR does not exist: $archive" }
        val profile = providers.gradleProperty("profile").getOrElse("prod")
        commandLine(jdkExecutable("java"), "-Dspring.profiles.active=$profile", "-jar", archive.absolutePath)
    }
}
