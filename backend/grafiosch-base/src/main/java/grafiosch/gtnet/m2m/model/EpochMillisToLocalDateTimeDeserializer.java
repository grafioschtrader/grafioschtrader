package grafiosch.gtnet.m2m.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

/**
 * Deserializes timestamps into {@link LocalDateTime} from both numeric (epoch millis)
 * and string (ISO-8601) formats. Required because Jackson 3.x rejects raw numeric
 * timestamps for LocalDateTime, and M2M peers may send either format depending on
 * their Jackson configuration. Numeric values are interpreted as UTC epoch millis.
 */
public class EpochMillisToLocalDateTimeDeserializer extends StdDeserializer<LocalDateTime> {

  public EpochMillisToLocalDateTimeDeserializer() {
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
