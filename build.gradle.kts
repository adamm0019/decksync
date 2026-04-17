// Version pins — sourced from each project's official channel at build time.
//   Spring Boot 3.4.1            — spring.io (stable, released 2024-12-19)
//   Spring Dep Mgmt plugin 1.1.7 — plugins.gradle.org
//   Picocli 4.7.6                — Maven Central: info.picocli
//   WireMock 3.9.1               — Maven Central: org.wiremock (not in Boot BOM)
// JUnit 5, AssertJ, Mockito, Jackson, SnakeYAML versions come from the
// Spring Boot BOM via the dependency-management plugin — don't pin directly.
plugins {
    java
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
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

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("info.picocli:picocli:$picocliVersion")
    implementation("info.picocli:picocli-spring-boot-starter:$picocliVersion")
    implementation("org.yaml:snakeyaml")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.wiremock:wiremock:$wiremockVersion")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
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
