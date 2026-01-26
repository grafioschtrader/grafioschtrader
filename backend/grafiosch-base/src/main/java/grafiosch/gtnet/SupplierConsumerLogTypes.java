package grafiosch.gtnet;

/**
 * Defines logging levels for GTNet exchange operations, separate for supplier and consumer roles.
 * This enum controls the granularity of logging for each role in the data exchange process.
 */
public enum SupplierConsumerLogTypes {

  /** Logging is disabled for this role. */
  SCL_OFF((byte) 0),

  /** Overview logging is enabled. Exchange statistics are recorded. */
  SCL_OVERVIEW((byte) 1),

  /** Detailed logging is enabled. Includes overview logging plus detailed audit trail. */
  SCL_DETAIL((byte) 2);

  private final Byte value;

  private SupplierConsumerLogTypes(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  /**
   * Returns the SupplierConsumerLogTypes enum constant for the given byte value.
   *
   * @param value the byte value to look up
   * @return the corresponding enum constant, or null if not found
   */
  public static SupplierConsumerLogTypes getSupplierConsumerLogType(byte value) {
    for (SupplierConsumerLogTypes logType : SupplierConsumerLogTypes.values()) {
      if (logType.getValue() == value) {
        return logType;
      }
    }
    return null;
  }

  /**
   * Checks if this log type enables any form of logging.
   *
   * @return true if logging is enabled (SCL_OVERVIEW or SCL_DETAIL)
   */
  public boolean isLoggingEnabled() {
    return this != SCL_OFF;
  }
}
