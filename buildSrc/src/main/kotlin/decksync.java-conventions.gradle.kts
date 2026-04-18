import net.ltgt.gradle.errorprone.errorprone

// Shared Java conventions applied to every DeckSync module. Keeps toolchain,
// encoding, warnings-as-errors, spotless, and errorprone identical everywhere.

plugins {
    java
    id("com.diffplug.spotless")
    id("net.ltgt.errorprone")
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

dependencies {
    "errorprone"("com.google.errorprone:error_prone_core:2.31.0")
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
    // SnakeYAML expands the ~17 MB Ludusavi manifest into a few hundred MB of
    // Java objects during parsing; give each worker JVM enough headroom to
    // handle it without OOM while other tests (which use far less) are
    // unaffected.
    maxHeapSize = "2g"
}
