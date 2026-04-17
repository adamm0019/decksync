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
