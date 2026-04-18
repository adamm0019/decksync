package dev.decksync.application;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Expands Ludusavi-style placeholders (e.g. {@code <base>}, {@code <winAppData>}) into concrete
 * absolute-path strings for the current host. Sealed: a fresh DeckSync run picks exactly one
 * implementation at startup based on the detected OS. Implementations are pure functions of ({@link
 * Environment}, {@link ExpansionContext}, raw string) — no disk or registry access. Anything that
 * needs IO belongs in a port further out.
 */
public sealed interface PlaceholderResolver
    permits PlaceholderResolver.Windows, PlaceholderResolver.Linux {

  /** Matches the {@code <token>} literals that Ludusavi uses in its save paths. */
  Pattern TOKEN = Pattern.compile("<(\\w+)>");

  Environment env();

  /**
   * Replaces every {@code <token>} in {@code raw} with its expanded value. Throws if any token is
   * unknown to this resolver.
   */
  default String expand(String raw, ExpansionContext ctx) {
    Objects.requireNonNull(raw, "raw");
    Objects.requireNonNull(ctx, "ctx");
    Matcher matcher = TOKEN.matcher(raw);
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      String token = matcher.group(1);
      String value = resolveToken(token, ctx);
      if (value == null) {
        throw new IllegalArgumentException(
            "Unknown or unsupported placeholder <" + token + "> in: " + raw);
      }
      matcher.appendReplacement(out, Matcher.quoteReplacement(value));
    }
    matcher.appendTail(out);
    return out.toString();
  }

  /**
   * Returns the expanded value for {@code token}, or {@code null} if this resolver does not
   * recognise the token. Called by {@link #expand} for each match.
   */
  String resolveToken(String token, ExpansionContext ctx);

  /** Tokens that mean the same thing regardless of host OS. */
  private static String commonToken(Environment env, String token, ExpansionContext ctx) {
    return switch (token) {
      case "base", "game" -> ctx.installBase().toString();
      case "root" -> ctx.installRoot().toString();
      case "storeUserId" ->
          ctx.storeUserId()
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "Placeholder <storeUserId> requires a store user id in context"));
      case "home" -> env.home().toString();
      case "osUserName" -> env.userName();
      default -> null;
    };
  }

  /** Windows host expansion. */
  record Windows(Environment env) implements PlaceholderResolver {

    public Windows {
      Objects.requireNonNull(env, "env");
    }

    @Override
    public String resolveToken(String token, ExpansionContext ctx) {
      String shared = commonToken(env, token, ctx);
      if (shared != null) {
        return shared;
      }
      return switch (token) {
        case "winAppData" ->
            env.get("APPDATA").orElseGet(() -> env.home().resolve("AppData/Roaming").toString());
        case "winLocalAppData" ->
            env.get("LOCALAPPDATA").orElseGet(() -> env.home().resolve("AppData/Local").toString());
        case "winDocuments" -> env.home().resolve("Documents").toString();
        case "winPublic" -> env.get("PUBLIC").orElse("C:\\Users\\Public");
        case "winDir" -> env.get("WINDIR").or(() -> env.get("SYSTEMROOT")).orElse("C:\\Windows");
        default -> null;
      };
    }
  }

  /** Linux host expansion. Windows placeholders are resolved via Proton prefix in a later phase. */
  record Linux(Environment env) implements PlaceholderResolver {

    public Linux {
      Objects.requireNonNull(env, "env");
    }

    @Override
    public String resolveToken(String token, ExpansionContext ctx) {
      String shared = commonToken(env, token, ctx);
      if (shared != null) {
        return shared;
      }
      return switch (token) {
        case "xdgConfig" ->
            env.get("XDG_CONFIG_HOME").orElseGet(() -> env.home().resolve(".config").toString());
        case "xdgData" ->
            env.get("XDG_DATA_HOME").orElseGet(() -> env.home().resolve(".local/share").toString());
        default -> null;
      };
    }
  }
}
