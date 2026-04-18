package dev.decksync.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "dev.decksync", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

  // Layers are declared `optionalLayer` so an empty package doesn't count as a
  // violation — during early M2 commits, most layers contain only a
  // package-info.java. The direction rules still apply once real classes land.
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
          .optionalLayer("cli")
          .definedBy("dev.decksync.cli..")
          .optionalLayer("gui")
          .definedBy("dev.decksync.gui..")
          .optionalLayer("config")
          .definedBy("dev.decksync.config..")
          .whereLayer("config")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("infrastructure")
          .mayOnlyBeAccessedByLayers("config")
          .whereLayer("web")
          .mayOnlyBeAccessedByLayers("config")
          .whereLayer("cli")
          .mayOnlyBeAccessedByLayers("config")
          .whereLayer("gui")
          .mayOnlyBeAccessedByLayers("cli", "config")
          .whereLayer("application")
          .mayOnlyBeAccessedByLayers("infrastructure", "web", "cli", "gui", "config");

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
              "dev.decksync.cli..",
              "dev.decksync.gui..",
              "dev.decksync.config..");
}
