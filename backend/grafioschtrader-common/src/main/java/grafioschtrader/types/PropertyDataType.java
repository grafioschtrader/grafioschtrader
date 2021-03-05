package grafioschtrader.types;

public enum PropertyDataType {
  DATA_TYPE_INT((byte) 0), DATA_TYPE_STRING((byte) 1), DATA_TYPE_DATE((byte) 2);

  private final Byte value;

  private PropertyDataType(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }
}
