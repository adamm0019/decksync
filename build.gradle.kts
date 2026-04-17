import net.ltgt.gradle.errorprone.errorprone

// Version pins — sourced from each project's official channel at build time.
//   Spring Boot 3.4.1            — spring.io (stable, released 2024-12-19)
//   Spring Dep Mgmt plugin 1.1.7 — plugins.gradle.org
//   Picocli 4.7.6                — Maven Central: info.picocli
//   WireMock 3.9.1               — Maven Central: org.wiremock (not in Boot BOM)
//   Spotless plugin 7.0.2        — Gradle Plugin Portal: com.diffplug.spotless
//   Google Java Format 1.24.0    — github.com/google/google-java-format
//   ErrorProne plugin 4.1.0      — Gradle Plugin Portal: net.ltgt.errorprone
//   ErrorProne core 2.31.0       — Maven Central: com.google.errorprone
//   ArchUnit 1.3.0               — Maven Central: com.tngtech.archunit (test only)
// JUnit 5, AssertJ, Mockito, Jackson, SnakeYAML versions come from the
// Spring Boot BOM via the dependency-management plugin — don't pin directly.
plugins {
    java
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "7.0.2"
    id("net.ltgt.errorprone") version "4.1.0"
}

group = "dev.decksync"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

repositories {
    mavenCentral()
}

// Dedicated source set for end-to-end / two-JVM tests — kept separate from
// unit tests so `./gradlew test` stays fast and `./gradlew integrationTest`
// can be run on demand or as part of `check`.
sourceSets {
    create("integrationTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

val integrationTestImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}
val integrationTestRuntimeOnly: Configuration by configurations.getting {
    extendsFrom(configurations.testRuntimeOnly.get())
}

val picocliVersion = "4.7.6"
val wiremockVersion = "3.9.1"
val archunitVersion = "1.3.0"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("info.picocli:picocli:$picocliVersion")
    implementation("info.picocli:picocli-spring-boot-starter:$picocliVersion")
    implementation("org.yaml:snakeyaml")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.wiremock:wiremock-standalone:$wiremockVersion")
    testImplementation("com.tngtech.archunit:archunit-junit5:$archunitVersion")

    errorprone("com.google.errorprone:error_prone_core:2.31.0")
}

spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat("1.24.0")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    options.errorprone {
        disableWarningsInGeneratedCode.set(true)
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // SnakeYAML expands the ~17 MB Ludusavi manifest into a few hundred MB of Java
    // objects during parsing; give each worker JVM enough headroom to handle it
    // without OOM while other tests (which use far less) are unaffected.
    maxHeapSize = "2g"
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Runs two-JVM end-to-end sync tests."
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    shouldRunAfter(tasks.test)
}

tasks.check {
    dependsOn(integrationTest)
}

springBoot {
    mainClass = "dev.decksync.DeckSyncApplication"
}

// Ludusavi manifest — the save-path database DeckSync uses to resolve games across OSes.
// Pinned to a specific upstream commit so the resolution logic is reproducible; bump the
// SHA intentionally when we want newer game coverage. The file is ~17 MB, so we download
// it at build time (cached per-SHA under build/) rather than checking it into git.
val ludusaviManifestSha = "d22e98100d6132c813d420644dde7445e701b416"
val ludusaviManifestDir = layout.buildDirectory.dir("ludusavi-manifest")
val ludusaviManifestFile = ludusaviManifestDir.map { it.file("ludusavi/manifest.yaml") }

val downloadLudusaviManifest =
    tasks.register("downloadLudusaviManifest") {
        description = "Downloads the pinned Ludusavi manifest YAML into the build directory."
        group = "build"
        inputs.property("sha", ludusaviManifestSha)
        outputs.file(ludusaviManifestFile)
        doLast {
            val target = ludusaviManifestFile.get().asFile
            target.parentFile.mkdirs()
            val source =
                uri(
                    "https://raw.githubusercontent.com/mtkennerly/ludusavi-manifest/" +
                        "$ludusaviManifestSha/data/manifest.yaml",
                )
                    .toURL()
            source.openStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

sourceSets.main.get().resources.srcDir(downloadLudusaviManifest.map { ludusaviManifestDir })

// Windows MSI packaging via jpackage. The Spring Boot fat jar already has the right
// Main-Class (JarLauncher) in its manifest, so jpackage treats it like any other
// runnable jar and jlinks a bundled JRE alongside it. `--add-modules ALL-MODULE-PATH`
// keeps us from chasing module-detection bugs — image size is secondary for a LAN tool.
// Requires WiX 3.x on PATH (jpackage shells out to it for MSI generation).
tasks.register<Exec>("packageMsi") {
    description = "Builds a Windows MSI installer with a bundled JRE via jpackage. Requires WiX 3.x on PATH."
    group = "distribution"
    dependsOn(tasks.bootJar)

    val bootJarFile = tasks.bootJar.flatMap { it.archiveFile }
    val inputDir = layout.buildDirectory.dir("jpackage-input")
    val outputDir = layout.buildDirectory.dir("distributions")
    val appVersion = project.version.toString().removeSuffix("-SNAPSHOT")

    inputs.file(bootJarFile)
    outputs.dir(outputDir)

    onlyIf {
        val os = System.getProperty("os.name").lowercase()
        val windows = os.contains("win")
        if (!windows) logger.warn("Skipping packageMsi — requires Windows (current os.name: $os).")
        windows
    }

    doFirst {
        val inDir = inputDir.get().asFile
        inDir.deleteRecursively()
        inDir.mkdirs()
        val jar = bootJarFile.get().asFile
        jar.copyTo(inDir.resolve(jar.name), overwrite = true)
        outputDir.get().asFile.mkdirs()

        commandLine(
            "jpackage",
            "--type", "msi",
            "--name", "DeckSync",
            "--app-version", appVersion,
            "--vendor", "DeckSync",
            "--description", "LAN peer-to-peer game save sync.",
            "--input", inDir.absolutePath,
            "--main-jar", jar.name,
            "--dest", outputDir.get().asFile.absolutePath,
            "--win-console",
            "--win-menu",
            "--win-menu-group", "DeckSync",
            "--win-shortcut",
            // Stable UUID so successive MSI versions upgrade in place rather than
            // installing side-by-side. Do NOT change once a release has shipped.
            "--win-upgrade-uuid", "6f3a9b4e-5c2d-4f78-8e91-a5b8d7c9e0f1",
            "--add-modules", "ALL-MODULE-PATH",
        )
    }
}

// Linux AppImage packaging. Unlike jpackage's native bundlers, AppImage isn't a
// first-class jpackage target — we assemble the AppDir ourselves and hand it to
// appimagetool. The runtime is jlinked from the host JDK, so this must run on a
// Linux host (a Windows jlink output would be Windows-specific binaries that
// wouldn't work on SteamOS).
tasks.register("packageAppImage") {
    description = "Builds a Linux AppImage with a bundled JRE via appimagetool. Requires appimagetool on PATH."
    group = "distribution"
    dependsOn(tasks.bootJar)

    val bootJarFile = tasks.bootJar.flatMap { it.archiveFile }
    val stagingDir = layout.buildDirectory.dir("appimage")
    val outputDir = layout.buildDirectory.dir("distributions")
    val appVersion = project.version.toString().removeSuffix("-SNAPSHOT")

    inputs.file(bootJarFile)
    outputs.dir(outputDir)

    onlyIf {
        val os = System.getProperty("os.name").lowercase()
        val linux = os.contains("linux")
        if (!linux) logger.warn("Skipping packageAppImage — requires Linux (current os.name: $os).")
        linux
    }

    doLast {
        val staging = stagingDir.get().asFile
        staging.deleteRecursively()
        val appDir = staging.resolve("DeckSync.AppDir")
        val binDir = appDir.resolve("usr/bin").apply { mkdirs() }
        val libDir = appDir.resolve("usr/lib").apply { mkdirs() }
        val runtimeDir = appDir.resolve("usr/runtime")

        val jar = bootJarFile.get().asFile
        jar.copyTo(libDir.resolve("decksync.jar"), overwrite = true)

        val javaHome = System.getProperty("java.home")
        val jlink = file("$javaHome/bin/jlink")
        exec {
            commandLine(
                jlink.absolutePath,
                "--add-modules", "ALL-MODULE-PATH",
                "--strip-debug",
                "--no-header-files",
                "--no-man-pages",
                "--compress", "2",
                "--output", runtimeDir.absolutePath,
            )
        }

        // AppRun is the entry-point the AppImage mounts and executes. Resolve the
        // real path so relative lookups work regardless of where the AppImage is run.
        appDir.resolve("AppRun").apply {
            writeText(
                """
                #!/bin/sh
                HERE="${'$'}(dirname "${'$'}(readlink -f "${'$'}0")")"
                exec "${'$'}HERE/usr/runtime/bin/java" -jar "${'$'}HERE/usr/lib/decksync.jar" "${'$'}@"
                """.trimIndent() + "\n",
            )
            setExecutable(true, false)
        }
        // Thin wrapper so `decksync` also works if the AppImage is extracted and
        // usr/bin is added to PATH — matches what installers usually expose.
        binDir.resolve("decksync").apply {
            writeText(
                """
                #!/bin/sh
                exec "${'$'}(dirname "${'$'}0")/../runtime/bin/java" -jar "${'$'}(dirname "${'$'}0")/../lib/decksync.jar" "${'$'}@"
                """.trimIndent() + "\n",
            )
            setExecutable(true, false)
        }

        appDir.resolve("decksync.desktop").writeText(
            """
            [Desktop Entry]
            Name=DeckSync
            Comment=LAN peer-to-peer game save sync
            Exec=decksync
            Icon=decksync
            Type=Application
            Categories=Utility;
            Terminal=true
            """.trimIndent() + "\n",
        )

        // Minimal SVG icon so appimagetool doesn't warn. Placeholder until we ship
        // real branding — the colour is Steam-ish blue, the shape is a square.
        appDir.resolve("decksync.svg").writeText(
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 16 16\">" +
                "<rect width=\"16\" height=\"16\" fill=\"#1b2838\"/>" +
                "<rect x=\"3\" y=\"3\" width=\"10\" height=\"10\" fill=\"#66c0f4\"/>" +
                "</svg>\n",
        )

        outputDir.get().asFile.mkdirs()
        val outFile = outputDir.get().asFile.resolve("DeckSync-$appVersion-x86_64.AppImage")
        exec {
            commandLine("appimagetool", appDir.absolutePath, outFile.absolutePath)
        }
    }
}
