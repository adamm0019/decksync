// buildSrc exists so shared Gradle wiring — toolchain, spotless, errorprone,
// compile args — lives in one place and is applied uniformly across the three
// modules (`:core`, `:gui`, `:cli`) via convention plugins.

plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    // Batik is published to Maven Central, not the Gradle plugin portal.
    // buildSrc needs both so the icon generation task can pull in the SVG
    // transcoder at plugin-classpath scope without leaking into any
    // module's runtime.
    mavenCentral()
}

dependencies {
    // Pin versions in buildSrc rather than each module, so bumping a plugin
    // is a one-line change. These mirror the versions that were in the
    // single-module build.gradle.kts before the M7a split.
    implementation("com.diffplug.spotless:spotless-plugin-gradle:7.0.2")
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:4.1.0")
    // Drives the :generateIcons task (SVG → PNG rasterisation). Lives in
    // buildSrc so there's one pinned version and no runtime classpath
    // pollution. ICO assembly is hand-rolled inside the task class — Batik
    // doesn't write ICO, and pulling in image4j/TwelveMonkeys for ~30 lines
    // of format glue isn't worth it.
    implementation("org.apache.xmlgraphics:batik-transcoder:1.17")
    implementation("org.apache.xmlgraphics:batik-codec:1.17")
}
