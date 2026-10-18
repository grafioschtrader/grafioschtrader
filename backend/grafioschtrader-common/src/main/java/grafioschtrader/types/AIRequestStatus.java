package grafioschtrader.types;

/**
 * State of a round trip to a language model. These values are saved in the database and must not be changed.
 */
public enum AIRequestStatus {

  /** Reserved before the call; stays so when the process dies during the call. It counts against the budgets. */
  RESERVED((byte) 0),

  /** The model answered. */
  SUCCEEDED((byte) 1),

  /** The call failed; it still counts, because its billing state is unknown. */
  FAILED((byte) 2);

  private final Byte value;

  private AIRequestStatus(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static AIRequestStatus getByValue(byte value) {
    for (AIRequestStatus status : AIRequestStatus.values()) {
      if (status.getValue() == value) {
        return status;
      }
    }
    return null;
  }
}
