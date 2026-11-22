package grafioschtrader.types;

/** Types of system-wide security actions. */
public enum SecurityActionType {
  ISIN_CHANGE((byte) 0), SPIN_OFF((byte) 1);

  private final byte value;

  SecurityActionType(byte value) {
    this.value = value;
  }

  public byte getValue() {
    return value;
  }

  public static SecurityActionType getByValue(byte value) {
    for (SecurityActionType entry : values()) {
      if (entry.value == value) {
        return entry;
      }
    }
    throw new IllegalArgumentException("Unknown SecurityActionType: " + value);
  }
}
