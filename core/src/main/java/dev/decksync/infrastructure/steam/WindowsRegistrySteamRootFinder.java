package dev.decksync.infrastructure.steam;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Reads the Steam root from {@code HKCU\Software\Valve\Steam\SteamPath} by shelling out to the
 * {@code reg} CLI. Steam writes this value as a forward-slash path (e.g. {@code c:/program files
 * (x86)/steam}), which {@link Path#of(String, String...)} handles natively on Windows. Any failure
 * — missing key, non-Windows host, unparseable output — resolves to {@link Optional#empty()} so the
 * locator can fall back to a well-known install path.
 */
public final class WindowsRegistrySteamRootFinder implements SteamRootFinder {

  private static final List<String> COMMAND =
      List.of("reg", "query", "HKCU\\Software\\Valve\\Steam", "/v", "SteamPath");

  @Override
  public Optional<Path> find() {
    return runRegQuery().flatMap(WindowsRegistrySteamRootFinder::parse);
  }

  private static Optional<String> runRegQuery() {
    try {
      Process process = new ProcessBuilder(COMMAND).redirectErrorStream(true).start();
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      if (!process.waitFor(5, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        return Optional.empty();
      }
      return process.exitValue() == 0 ? Optional.of(output) : Optional.empty();
    } catch (IOException e) {
      return Optional.empty();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    }
  }

  static Optional<Path> parse(String regOutput) {
    for (String line : regOutput.split("\\R", -1)) {
      String trimmed = line.trim();
      int nameEnd = 0;
      while (nameEnd < trimmed.length() && !Character.isWhitespace(trimmed.charAt(nameEnd))) {
        nameEnd++;
      }
      if (nameEnd == 0 || !"SteamPath".equals(trimmed.substring(0, nameEnd))) {
        continue;
      }
      String rest = trimmed.substring(nameEnd).trim();
      if (!rest.startsWith("REG_SZ")) {
        continue;
      }
      String value = rest.substring("REG_SZ".length()).trim();
      if (value.isEmpty()) {
        return Optional.empty();
      }
      try {
        return Optional.of(Path.of(value));
      } catch (InvalidPathException e) {
        return Optional.empty();
      }
    }
    return Optional.empty();
  }
}
