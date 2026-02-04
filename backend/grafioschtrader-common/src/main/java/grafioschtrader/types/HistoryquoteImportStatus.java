package grafioschtrader.types;

/**
 * Status values for tracking historical price data import during GTNet security import.
 *
 * When securities are created via GTNet import, this status tracks the progress and outcome
 * of the subsequent historical price data loading process.
 */
public enum HistoryquoteImportStatus {

  /**
   * Historical import has not yet been attempted.
   * This is the default status when a security is first created via GTNet import.
   */
  PENDING((byte) 0),

  /**
   * Historical price data was successfully loaded from a GTNet peer.
   * The idGtNetHistoryquote field indicates which peer provided the data.
   */
  GTNET_LOADED((byte) 1),

  /**
   * Historical price data was loaded using the connector fallback mechanism.
   * This occurs when no GTNet peer could provide the data.
   */
  CONNECTOR_LOADED((byte) 2),

  /**
   * All attempts to load historical data failed.
   * Both GTNet peers and connector fallback were unsuccessful.
   */
  FAILED((byte) 3);

  private final byte value;

  HistoryquoteImportStatus(byte value) {
    this.value = value;
  }

  public byte getValue() {
    return this.value;
  }

  /**
   * Returns the enum constant for the given byte value.
   *
   * @param value the byte value to look up
   * @return the corresponding HistoryquoteImportStatus, or null if not found
   */
  public static HistoryquoteImportStatus getByValue(byte value) {
    for (HistoryquoteImportStatus status : values()) {
      if (status.getValue() == value) {
        return status;
      }
    }
    return null;
  }
}
