package grafiosch.config;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * Global Jackson configuration for handling LocalDateTime deserialization from both
 * numeric (epoch millis) and string (ISO-8601) formats. Required because Jackson 3.x
 * rejects raw numeric timestamps for LocalDateTime, but M2M peers running older versions
 * may send either format.
 */
@Configuration
public class JacksonConfig {

  @Bean
  public SimpleModule localDateTimeModule() {
    SimpleModule module = new SimpleModule("LocalDateTimeModule");
    module.addDeserializer(LocalDateTime.class, new FlexibleLocalDateTimeDeserializer());
    return module;
  }

  private static class FlexibleLocalDateTimeDeserializer extends StdDeserializer<LocalDateTime> {

    FlexibleLocalDateTimeDeserializer() {
      super(LocalDateTime.class);
    }

    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) {
      if (p.currentToken() == JsonToken.VALUE_NUMBER_INT) {
        long epochMillis = p.getLongValue();
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC);
      }
      return LocalDateTime.parse(p.getText());
    }
  }
}
