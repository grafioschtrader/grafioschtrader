package grafiosch.types;

public enum UDFShareMode {
  /** Private values of the owner. */
  UDF_SHARE_NONE((byte) 0),
  /** Subscribers can read the common values. */
  UDF_SHARE_READ((byte) 1),
  /** Subscribers can read and edit the common values. */
  UDF_SHARE_READ_WRITE((byte) 2);

  private final byte value;

  UDFShareMode(byte value) {
    this.value = value;
  }

  public byte getValue() {
    return value;
  }

  public static UDFShareMode getByValue(byte value) {
    for (UDFShareMode mode : values()) {
      if (mode.value == value) {
        return mode;
      }
    }
    throw new IllegalArgumentException("Unknown UDF share mode: " + value);
  }
}
