package dev.decksync.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces the hexagonal boundary for {@code :core}. After the M7a module split, {@code :gui} and
 * {@code :cli} live in separate Gradle modules and aren't on this test's classpath — we no longer
 * pretend to police them here. Both the access direction (who may import whom) and the domain's
 * framework-freeness are asserted; an extra rule enforces the M7b tightening that application code
 * only depends on the domain layer.
 */
@AnalyzeClasses(packages = "dev.decksync", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

  @ArchTest
  static final ArchRule hexagonalLayers =
      layeredArchitecture()
          .consideringOnlyDependenciesInLayers()
          .optionalLayer("domain")
          .definedBy("dev.decksync.domain..")
          .optionalLayer("application")
          .definedBy("dev.decksync.application..")
          .optionalLayer("infrastructure")
          .definedBy("dev.decksync.infrastructure..")
          .optionalLayer("web")
          .definedBy("dev.decksync.web..")
          .optionalLayer("config")
          .definedBy("dev.decksync.config..")
          // Access direction — who may be imported by whom.
          .whereLayer("config")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("infrastructure")
          .mayOnlyBeAccessedByLayers("config")
          .whereLayer("web")
          .mayOnlyBeAccessedByLayers("config")
          .whereLayer("application")
          .mayOnlyBeAccessedByLayers("infrastructure", "web", "config")
          // Outgoing edges — what each layer may depend on. Domain is the
          // nucleus; application only leans inward onto domain. Infrastructure
          // and web implement application ports, so they may reach domain and
          // application. Config wires everything together and is allowed to
          // touch any lower layer.
          .whereLayer("domain")
          .mayNotAccessAnyLayer()
          .whereLayer("application")
          .mayOnlyAccessLayers("domain");

  @ArchTest
  static final ArchRule domainIsFrameworkFree =
      noClasses()
          .that()
          .resideInAPackage("dev.decksync.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "picocli..",
              "com.fasterxml.jackson..",
              "org.yaml..",
              "javafx..",
              "dev.decksync.application..",
              "dev.decksync.infrastructure..",
              "dev.decksync.web..",
              "dev.decksync.config..");
}
