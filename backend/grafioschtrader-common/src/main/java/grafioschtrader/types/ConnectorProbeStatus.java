package grafioschtrader.types;

/**
 * Outcome of a trial request against a price, dividend or split connector for an instrument that does not exist yet in
 * GT. The probe runs on a transient security that is never saved; it tells the user whether a proposed connector with
 * its derived URL extension actually delivers data.
 */
public enum ConnectorProbeStatus {

  /** The connector delivered data, e.g. at least one closing price. */
  OK,

  /**
   * The connector answered without error but delivered no data, which is normal for a dividend connector of an
   * instrument that pays nothing.
   */
  NO_DATA,

  /** The derived URL extension does not satisfy the regular expression of the connector. */
  REGEX_FAILED,

  /**
   * The connector rejects the setting for another reason than its URL pattern, e.g. because the listing is not on an
   * exchange it serves or lacks an ISIN.
   */
  CHECK_FAILED,

  /**
   * A dividend or split connector delivered no events, but the same connector with the same URL extension delivered no
   * prices either: the symbol is most likely unknown to the provider, so the empty answer proves nothing.
   */
  UNKNOWN_SYMBOL,

  /** The request failed or the answer could not be read. */
  REQUEST_FAILED,

  /** The connector did not answer within the deadline of the lookup. */
  TIMEOUT
}
