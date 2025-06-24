package grafiosch.dynamic.model;

/**
 * Data type as used in the GUI. These may be different from Java's data type.
 *
 */
public enum DataType {

  None((byte) 0),
  /** Decimal numbers */
  Numeric((byte) 1),
  /** Only Integer */
  NumericInteger((byte) 4),
  /** Only String */
  String((byte) 7),
  /** Date with time */
  DateTimeNumeric((byte) 8),
  /** Only Date */
  DateString((byte) 10),
  /** True or false */
  Boolean((byte) 13),
  /** For a web link. Normally this is a string with validation for validity as a URL. */
  URLString((byte) 20);

  private final Byte value;

  private DataType(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static DataType getDataType(byte value) {
    for (DataType dataType : DataType.values()) {
      if (dataType.getValue() == value) {
        return dataType;
      }
    }
    return null;
  }
}
