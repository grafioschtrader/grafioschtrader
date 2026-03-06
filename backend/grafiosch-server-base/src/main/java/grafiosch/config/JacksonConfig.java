package grafiosch.config;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * Global Jackson configuration for handling LocalDateTime deserialization from
 * numeric (epoch millis), plain ISO-8601 ("2025-03-06T12:00:00"), and
 * zoned ISO-8601 ("2025-03-06T12:00:00Z", "2025-03-06T12:00:00+02:00") formats.
 * Required because Jackson 3.x rejects raw numeric timestamps for LocalDateTime,
 * and M2M peers may send any of these formats.
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
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(p.getLongValue()), ZoneOffset.UTC);
      }
      String text = p.getText();
      try {
        return LocalDateTime.parse(text);
      } catch (DateTimeParseException e) {
        return ZonedDateTime.parse(text, DateTimeFormatter.ISO_DATE_TIME).toLocalDateTime();
      }
    }
  }
}
