package grafioschtrader.types;

/** Booking roles within a security action application. */
public enum SecurityActionRole {
  SPIN_OFF_SALE((byte) 1), SPIN_OFF_REPURCHASE((byte) 2), SPIN_OFF_CHILD((byte) 3);

  private final byte value;

  SecurityActionRole(byte value) {
    this.value = value;
  }

  public byte getValue() {
    return value;
  }

  public static SecurityActionRole getByValue(byte value) {
    for (SecurityActionRole entry : values()) {
      if (entry.value == value) {
        return entry;
      }
    }
    throw new IllegalArgumentException("Unknown SecurityActionRole: " + value);
  }
}
