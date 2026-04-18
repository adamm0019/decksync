// :gui is the JavaFX surface. Depends on :core for engine services; has no
// Spring Boot plugin of its own (the Picocli entry in :cli boots Spring and
// picks up beans from both :core and :gui via component scan).

plugins {
    id("decksync.java-conventions")
    id("io.spring.dependency-management") version "1.1.7"
    // JavaFX plugin resolves per-OS classifier artifacts (mac/win/linux)
    // automatically. 0.1.0 is pinned because upstream ships breaking Gradle
    // API changes between minor versions.
    id("org.openjfx.javafxplugin") version "0.1.0"
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.4.1")
    }
}

javafx {
    version = "21.0.5"
    modules = listOf("javafx.controls", "javafx.fxml")
}

dependencies {
    implementation(project(":core"))
    implementation("org.springframework.boot:spring-boot-starter")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
