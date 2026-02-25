package grafioschtrader.types;

/**
 * Defines number format conventions used by data providers for parsing numeric values.
 * US uses comma as thousands separator and dot as decimal (1,234.56).
 * GERMAN uses dot as thousands separator and comma as decimal (1.234,56).
 * SWISS uses apostrophe as thousands separator and dot as decimal (1'234.56).
 * PLAIN has no thousands separator, dot as decimal (1234.56).
 */
public enum NumberFormatType {
  US((byte) 1),
  GERMAN((byte) 2),
  SWISS((byte) 3),
  PLAIN((byte) 4);

  private final Byte value;

  private NumberFormatType(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static NumberFormatType getByValue(byte value) {
    for (NumberFormatType type : NumberFormatType.values()) {
      if (type.getValue() == value) {
        return type;
      }
    }
    return null;
  }
}
