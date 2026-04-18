package dev.decksync.infrastructure.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import dev.decksync.domain.GameId;
import dev.decksync.domain.LogicalPath;
import dev.decksync.domain.Sha256;
import java.io.IOException;

/**
 * Jackson module for the {@link dev.decksync.domain.Manifest} wire format. Serializes and
 * deserializes {@link Sha256} as a 64-char hex string, {@link LogicalPath} as a bare string, and
 * {@link GameId} as a discriminated object so SteamAppId and Slug never collide (appid {@code 70}
 * and slug {@code "70"} are semantically different and must round-trip distinctly). Deserializers
 * keep the wire format symmetric so the HTTP sync client can parse exactly what the server emits.
 */
public final class ManifestJsonModule extends SimpleModule {

  private static final long serialVersionUID = 1L;

  public ManifestJsonModule() {
    super("decksync-manifest");
    addSerializer(Sha256.class, new Sha256Serializer());
    addSerializer(LogicalPath.class, new LogicalPathSerializer());
    addSerializer(GameId.class, new GameIdSerializer());
    addDeserializer(Sha256.class, new Sha256Deserializer());
    addDeserializer(LogicalPath.class, new LogicalPathDeserializer());
    addDeserializer(GameId.class, new GameIdDeserializer());
  }

  private static final class Sha256Serializer extends JsonSerializer<Sha256> {
    @Override
    public void serialize(Sha256 value, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
      gen.writeString(value.hex());
    }
  }

  private static final class LogicalPathSerializer extends JsonSerializer<LogicalPath> {
    @Override
    public void serialize(LogicalPath value, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
      gen.writeString(value.path());
    }
  }

  private static final class GameIdSerializer extends JsonSerializer<GameId> {
    @Override
    public void serialize(GameId value, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
      gen.writeStartObject();
      switch (value) {
        case GameId.SteamAppId s -> {
          gen.writeStringField("kind", "steam");
          gen.writeNumberField("appId", s.appid());
        }
        case GameId.Slug s -> {
          gen.writeStringField("kind", "slug");
          gen.writeStringField("value", s.value());
        }
      }
      gen.writeEndObject();
    }
  }

  private static final class Sha256Deserializer extends JsonDeserializer<Sha256> {
    @Override
    public Sha256 deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
      return Sha256.ofHex(parser.getValueAsString());
    }
  }

  private static final class LogicalPathDeserializer extends JsonDeserializer<LogicalPath> {
    @Override
    public LogicalPath deserialize(JsonParser parser, DeserializationContext ctxt)
        throws IOException {
      return new LogicalPath(parser.getValueAsString());
    }
  }

  private static final class GameIdDeserializer extends JsonDeserializer<GameId> {
    @Override
    public GameId deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
      JsonNode node = parser.getCodec().readTree(parser);
      JsonNode kindNode = node.get("kind");
      if (kindNode == null) {
        throw new IOException("GameId JSON missing 'kind' discriminator");
      }
      String kind = kindNode.asText();
      return switch (kind) {
        case "steam" -> new GameId.SteamAppId(node.get("appId").asLong());
        case "slug" -> new GameId.Slug(node.get("value").asText());
        default -> throw new IOException("Unknown GameId kind: " + kind);
      };
    }
  }
}
