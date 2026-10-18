package grafioschtrader.types;

/**
 * Fill state of one cell — one user-defined security field of one security — in {@code udf_data_fill}. All states of
 * every fill source are assigned here, so that a later fill source adds no renumbering. These values are saved in the
 * database and must not be changed.
 */
public enum UDFFillState {

  /** The fill wrote or adopted the value; it is automatic as long as the cell still holds the filled value. */
  APPLIED((byte) 0),

  /** The source answered definitively that it has no value for this security. */
  NOT_FOUND((byte) 1),

  /** The user took the cell over; nothing automatic touches it, whether it holds a value or not. */
  LOCKED((byte) 2),

  /** A verified proposal of a language model awaiting the decision of the user. */
  PROPOSED((byte) 3),

  /** The user rejected the proposal. */
  REJECTED((byte) 4),

  /** The proposal failed verification and was never shown to the user. */
  DISCARDED((byte) 5);

  private final Byte value;

  private UDFFillState(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static UDFFillState getByValue(byte value) {
    for (UDFFillState fillState : UDFFillState.values()) {
      if (fillState.getValue() == value) {
        return fillState;
      }
    }
    return null;
  }
}
