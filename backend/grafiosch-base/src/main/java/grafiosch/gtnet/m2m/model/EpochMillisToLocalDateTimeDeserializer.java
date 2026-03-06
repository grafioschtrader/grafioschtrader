package grafiosch.gtnet.m2m.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

/**
 * Deserializes epoch millisecond timestamps into {@link LocalDateTime} assuming UTC.
 * Required because Jackson 3.x rejects raw numeric timestamps for LocalDateTime
 * since it lacks timezone information. This deserializer bridges that gap for
 * M2M communication where timestamps are always UTC epoch millis.
 */
public class EpochMillisToLocalDateTimeDeserializer extends StdDeserializer<LocalDateTime> {

  public EpochMillisToLocalDateTimeDeserializer() {
    super(LocalDateTime.class);
  }

  @Override
  public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) {
    long epochMillis = p.getLongValue();
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC);
  }
}
