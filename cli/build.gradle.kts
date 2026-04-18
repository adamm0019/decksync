// :cli is the program entry point. Picocli parses argv, Spring Boot boots a
// context with Tomcat (for `serve`) or no web container (for one-shot
// commands). Depends on :core for engine beans and :gui so `decksync gui`
// can launch the JavaFX surface in the same JVM.

plugins {
    id("decksync.java-conventions")
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
    // :cli references GuiCommand → DeckSyncGuiApp (which extends
    // javafx.application.Application), so JavaFX must be resolvable at compile
    // time here as well — :gui declares it as `implementation` only. The same
    // plugin version/modules pin as :gui.
    id("org.openjfx.javafxplugin") version "0.1.0"
}

javafx {
    version = "21.0.5"
    modules = listOf("javafx.controls", "javafx.fxml")
}

val picocliVersion = "4.7.6"

dependencies {
    implementation(project(":core"))
    implementation(project(":gui"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("info.picocli:picocli:$picocliVersion")
    implementation("info.picocli:picocli-spring-boot-starter:$picocliVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

springBoot {
    mainClass = "dev.decksync.DeckSyncApplication"
    // Emits META-INF/build-info.properties into the boot jar so
    // DiscoveryConfiguration can read the real app version for the mDNS TXT
    // record instead of a "dev" placeholder.
    buildInfo()
}

// Pin jpackage + jlink to the Gradle Java 21 toolchain rather than whatever
// ambient JDK is on PATH. Without this, a system-wide Java 17 jpackage silently
// bundles a Java 17 runtime into the installer, and the Java 21 classes inside
// bootJar fail at startup with UnsupportedClassVersionError.
val javaToolchains = extensions.getByType<JavaToolchainService>()
val packagingLauncher = javaToolchains.launcherFor(java.toolchain)

val appVersion = project.version.toString().removeSuffix("-SNAPSHOT")

// Windows MSI packaging via jpackage. The Spring Boot fat jar already has the
// right Main-Class (JarLauncher) in its manifest, so jpackage treats it like
// any other runnable jar and jlinks a bundled JRE alongside it.
// `--add-modules ALL-MODULE-PATH` keeps us from chasing module-detection
// bugs — image size is secondary for a LAN tool.
// Requires WiX 3.x on PATH (jpackage shells out to it for MSI generation).
tasks.register<Exec>("packageMsi") {
    description = "Builds a Windows MSI installer with a bundled JRE via jpackage. Requires WiX 3.x on PATH."
    group = "distribution"
    dependsOn(tasks.bootJar)
    // packaging/decksync.ico is written by the root :generateIcons task from
    // the canonical SVG. Depend on it so a clean checkout that edits the SVG
    // gets a fresh ICO threaded through jpackage without a separate step.
    dependsOn(rootProject.tasks.named("generateIcons"))

    val bootJarFile = tasks.bootJar.flatMap { it.archiveFile }
    val inputDir = layout.buildDirectory.dir("jpackage-input")
    val outputDir = layout.buildDirectory.dir("distributions")
    val icoFile = rootProject.layout.projectDirectory.file("packaging/decksync.ico")

    inputs.file(bootJarFile)
    inputs.file(icoFile)
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

        val jdkHome = packagingLauncher.get().metadata.installationPath.asFile
        val jpackageExe = jdkHome.resolve("bin/jpackage.exe")

        commandLine(
            jpackageExe.absolutePath,
            "--type", "msi",
            "--name", "DeckSync",
            "--app-version", appVersion,
            "--vendor", "DeckSync",
            "--description", "LAN peer-to-peer game save sync.",
            "--input", inDir.absolutePath,
            "--main-jar", jar.name,
            "--dest", outputDir.get().asFile.absolutePath,
            // Multi-resolution ICO — jpackage stamps this onto the installer,
            // the Start Menu shortcut, and the executable resource, replacing
            // the default Java coffee-cup icon.
            "--icon", icoFile.asFile.absolutePath,
            "--win-console",
            "--win-menu",
            "--win-menu-group", "DeckSync",
            "--win-shortcut",
            // Stable UUID so successive MSI versions upgrade in place rather
            // than installing side-by-side. Do NOT change once a release has
            // shipped.
            "--win-upgrade-uuid", "6f3a9b4e-5c2d-4f78-8e91-a5b8d7c9e0f1",
            "--add-modules", "ALL-MODULE-PATH",
        )
    }
}

// Linux AppImage packaging. Unlike jpackage's native bundlers, AppImage isn't
// a first-class jpackage target — we assemble the AppDir ourselves and hand
// it to appimagetool. The runtime is jlinked from the host JDK, so this must
// run on a Linux host (a Windows jlink output would be Windows-specific
// binaries that wouldn't work on SteamOS).
tasks.register("packageAppImage") {
    description = "Builds a Linux AppImage with a bundled JRE via appimagetool. Requires appimagetool on PATH."
    group = "distribution"
    dependsOn(tasks.bootJar)
    // packaging/decksync.png is the 1024×1024 rasterisation of the canonical
    // SVG — appimagetool embeds it as the AppImage's thumbnail and the .desktop
    // file references it as the launcher icon.
    dependsOn(rootProject.tasks.named("generateIcons"))

    val bootJarFile = tasks.bootJar.flatMap { it.archiveFile }
    val stagingDir = layout.buildDirectory.dir("appimage")
    val outputDir = layout.buildDirectory.dir("distributions")
    val pngFile = rootProject.layout.projectDirectory.file("packaging/decksync.png")

    inputs.file(bootJarFile)
    inputs.file(pngFile)
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

        val jdkHome = packagingLauncher.get().metadata.installationPath.asFile
        val jlink = jdkHome.resolve("bin/jlink")
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

        // AppRun is the entry-point the AppImage mounts and executes. Resolve
        // the real path so relative lookups work regardless of where the
        // AppImage is run.
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
        // Thin wrapper so `decksync` also works if the AppImage is extracted
        // and usr/bin is added to PATH — matches what installers usually expose.
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

        // Real branding now — the canonical SVG rendered to a 1024×1024 PNG by
        // :generateIcons. appimagetool picks this up as the launcher thumbnail
        // because Icon=decksync in the .desktop entry matches the basename.
        pngFile.asFile.copyTo(appDir.resolve("decksync.png"), overwrite = true)

        outputDir.get().asFile.mkdirs()
        val outFile = outputDir.get().asFile.resolve("DeckSync-$appVersion-x86_64.AppImage")
        exec {
            commandLine("appimagetool", appDir.absolutePath, outFile.absolutePath)
        }
    }
}
