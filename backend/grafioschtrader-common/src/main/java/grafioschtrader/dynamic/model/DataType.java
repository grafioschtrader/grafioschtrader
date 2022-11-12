package grafioschtrader.dynamic.model;

/**
 * Data type as used in the GUI. These may be different from Java's datatype.
 *
 */
public enum DataType {

  None((byte) 0), Numeric((byte) 1), NumericInteger((byte) 4), String((byte) 7), 
  DateTimeNumeric((byte) 8), DateString((byte) 10),
  Boolean((byte) 13);

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
