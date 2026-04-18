// :core holds the DeckSync engine — domain + application + infrastructure +
// web + config. No JavaFX, no Picocli. Depends on Spring Boot for DI and the
// HTTP layer only. ArchUnit tests in :core's test source set enforce the
// hexagonal dependency rule at build time.

plugins {
    id("decksync.java-conventions")
    id("org.springframework.boot") version "3.4.1" apply false
    id("io.spring.dependency-management") version "1.1.7"
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.4.1")
    }
}

val wiremockVersion = "3.9.1"
val archunitVersion = "1.3.0"

// Dedicated source set for end-to-end / two-JVM tests — kept separate from
// unit tests so `./gradlew core:test` stays fast and `./gradlew core:integrationTest`
// runs on demand or as part of `check`.
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

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.yaml:snakeyaml")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.wiremock:wiremock-standalone:$wiremockVersion")
    testImplementation("com.tngtech.archunit:archunit-junit5:$archunitVersion")
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

// Ludusavi manifest — the save-path database DeckSync uses to resolve games
// across OSes. Pinned to a specific upstream commit so the resolution logic is
// reproducible; bump the SHA intentionally when we want newer game coverage.
// The file is ~17 MB, so we download it at build time (cached per-SHA under
// build/) rather than checking it into git.
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
