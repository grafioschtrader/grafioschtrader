package grafioschtrader.types;

/** How the history basis of a spin-off was decided. */
public enum SpinOffHistoryBasisOrigin {
  /** Measured as the ratio of the stored to the delivered series before and after the action date. */
  REFERENCE((byte) 1),
  /** Read from the price step of the delivered series on the action date. */
  STEP((byte) 2),
  /** Set by an administrator. */
  ADMINISTRATOR((byte) 3);

  private final byte value;

  SpinOffHistoryBasisOrigin(byte value) {
    this.value = value;
  }

  public byte getValue() {
    return value;
  }

  public static SpinOffHistoryBasisOrigin getByValue(byte value) {
    for (SpinOffHistoryBasisOrigin entry : values()) {
      if (entry.value == value) {
        return entry;
      }
    }
    throw new IllegalArgumentException("Unknown SpinOffHistoryBasisOrigin: " + value);
  }
}
