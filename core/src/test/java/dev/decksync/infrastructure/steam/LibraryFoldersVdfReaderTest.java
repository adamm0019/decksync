package dev.decksync.infrastructure.steam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import dev.decksync.application.SteamLibrary;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LibraryFoldersVdfReaderTest {

  @Test
  void readsSingleLibraryWithApps(@TempDir Path tempDir) {
    Path libA = tempDir.resolve("SteamLibrary");
    String vdf =
        """
        "libraryfolders"
        {
          "0"
          {
            "path" "%s"
            "apps"
            {
              "440" "12345"
              "730" "67890"
            }
          }
        }
        """
            .formatted(libA.toString().replace("\\", "\\\\"));

    List<SteamLibrary> libs = new LibraryFoldersVdfReader().read(vdf);

    assertThat(libs).hasSize(1);
    assertThat(libs.get(0).path()).isEqualTo(libA);
    assertThat(libs.get(0).appIds()).containsExactlyInAnyOrder(440L, 730L);
  }

  @Test
  void readsMultipleLibrariesInDocumentOrder(@TempDir Path tempDir) {
    Path libA = tempDir.resolve("A");
    Path libB = tempDir.resolve("B");
    Path libC = tempDir.resolve("C");
    String vdf =
        """
        "libraryfolders"
        {
          "0" { "path" "%s" "apps" {} }
          "1" { "path" "%s" "apps" { "1" "0" } }
          "2" { "path" "%s" "apps" { "2" "0" } }
        }
        """
            .formatted(
                libA.toString().replace("\\", "\\\\"),
                libB.toString().replace("\\", "\\\\"),
                libC.toString().replace("\\", "\\\\"));

    List<SteamLibrary> libs = new LibraryFoldersVdfReader().read(vdf);

    assertThat(libs).extracting(SteamLibrary::path).containsExactly(libA, libB, libC);
  }

  @Test
  void skipsEntryWithMissingPath(@TempDir Path tempDir) {
    Path libA = tempDir.resolve("A");
    String vdf =
        """
        "libraryfolders"
        {
          "0" { "label" "broken" }
          "1" { "path" "%s" "apps" {} }
        }
        """
            .formatted(libA.toString().replace("\\", "\\\\"));

    List<SteamLibrary> libs = new LibraryFoldersVdfReader().read(vdf);

    assertThat(libs).hasSize(1);
    assertThat(libs.get(0).path()).isEqualTo(libA);
  }

  @Test
  void skipsEntryWithRelativePath() {
    String vdf =
        """
        "libraryfolders"
        {
          "0" { "path" "relative/path" "apps" {} }
        }
        """;

    assertThat(new LibraryFoldersVdfReader().read(vdf)).isEmpty();
  }

  @Test
  void missingLibraryFoldersSectionYieldsEmpty() {
    String vdf = "\"somethingelse\" { \"k\" \"v\" }";

    assertThat(new LibraryFoldersVdfReader().read(vdf)).isEmpty();
  }

  @Test
  void libraryWithoutAppsSectionHasEmptyAppIds(@TempDir Path tempDir) {
    Path libA = tempDir.resolve("A");
    String vdf =
        """
        "libraryfolders"
        {
          "0" { "path" "%s" }
        }
        """
            .formatted(libA.toString().replace("\\", "\\\\"));

    assertThat(new LibraryFoldersVdfReader().read(vdf).get(0).appIds()).isEmpty();
  }

  @Test
  void skipsNonNumericAppIdKeys(@TempDir Path tempDir) {
    Path libA = tempDir.resolve("A");
    String vdf =
        """
        "libraryfolders"
        {
          "0"
          {
            "path" "%s"
            "apps"
            {
              "440" "0"
              "bogus" "0"
              "730" "0"
            }
          }
        }
        """
            .formatted(libA.toString().replace("\\", "\\\\"));

    assertThat(new LibraryFoldersVdfReader().read(vdf).get(0).appIds())
        .containsExactlyInAnyOrder(440L, 730L);
  }

  @Test
  void readsFromFile(@TempDir Path tempDir) throws IOException {
    Path libA = tempDir.resolve("lib");
    Path vdfFile = tempDir.resolve("libraryfolders.vdf");
    Files.writeString(
        vdfFile,
        """
        "libraryfolders"
        {
          "0" { "path" "%s" "apps" { "730" "0" } }
        }
        """
            .formatted(libA.toString().replace("\\", "\\\\")));

    List<SteamLibrary> libs = new LibraryFoldersVdfReader().read(vdfFile);

    assertThat(libs).hasSize(1);
    assertThat(libs.get(0).appIds()).containsExactly(730L);
  }

  @Test
  void rejectsNullArgs() {
    var reader = new LibraryFoldersVdfReader();

    assertThatNullPointerException().isThrownBy(() -> reader.read((String) null));
    assertThatNullPointerException().isThrownBy(() -> reader.read((Path) null));
    assertThatNullPointerException().isThrownBy(() -> reader.read((java.io.Reader) null));
    assertThatNullPointerException().isThrownBy(() -> new LibraryFoldersVdfReader(null));
  }
}
