package grafioschtrader.types;

/**
 * Defines the response format types supported by generic feed connectors for parsing data provider responses.
 */
public enum ResponseFormatType {
  JSON((byte) 1),
  CSV((byte) 2),
  HTML((byte) 3);

  private final Byte value;

  private ResponseFormatType(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static ResponseFormatType getByValue(byte value) {
    for (ResponseFormatType type : ResponseFormatType.values()) {
      if (type.getValue() == value) {
        return type;
      }
    }
    return null;
  }
}
