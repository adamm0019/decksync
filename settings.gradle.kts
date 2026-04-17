plugins {
    // Lets the Java toolchain auto-provision JDKs from Adoptium/Foojay.
    // Without this, a missing Java 21 on PATH would break the build; with it,
    // Gradle downloads the required JDK on first use.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

rootProject.name = "decksync"
