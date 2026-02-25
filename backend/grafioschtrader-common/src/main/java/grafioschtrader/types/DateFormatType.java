package grafioschtrader.types;

/**
 * Defines date format types used in URL templates and response parsing by generic feed connectors.
 */
public enum DateFormatType {
  UNIX_SECONDS((byte) 1),
  UNIX_MILLIS((byte) 2),
  PATTERN((byte) 3),
  ISO_DATE((byte) 4),
  ISO_DATE_TIME((byte) 5);

  private final Byte value;

  private DateFormatType(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static DateFormatType getByValue(byte value) {
    for (DateFormatType type : DateFormatType.values()) {
      if (type.getValue() == value) {
        return type;
      }
    }
    return null;
  }
}
