package grafioschtrader.dynamic.model;

public enum DataType {

  None((byte) 0), Numeric((byte) 1), NumericInteger((byte) 3), String((byte) 6), DateString((byte) 9);

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
