// buildSrc exists so shared Gradle wiring — toolchain, spotless, errorprone,
// compile args — lives in one place and is applied uniformly across the three
// modules (`:core`, `:gui`, `:cli`) via convention plugins.

plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

dependencies {
    // Pin versions in buildSrc rather than each module, so bumping a plugin
    // is a one-line change. These mirror the versions that were in the
    // single-module build.gradle.kts before the M7a split.
    implementation("com.diffplug.spotless:spotless-plugin-gradle:7.0.2")
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:4.1.0")
}
