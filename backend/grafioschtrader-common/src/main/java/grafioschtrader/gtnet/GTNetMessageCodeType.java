package grafioschtrader.gtnet;

import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.GTNetMessageCode;

/**
 * Application-specific message codes for Grafioschtrader GTNet communication.
 *
 * This enum contains only Grafioschtrader-specific message codes (60+) for trading-related functionality like price
 * exchange, history quotes, and security lookup. Core protocol messages (0-54) are defined in
 * {@link GNetCoreMessageCode} in the grafiosch-base module.
 *
 * <h3>Naming Convention</h3> {@code _SEL_} marks a message aimed at one selected peer, {@code _ALL_} a broadcast.
 * Everything else a code does — its category, the answers it accepts, its payload model, whether it may appear in an
 * auto-answer rule — is declared in {@link GTProtocolDescriptors} and read from {@code GTNetMessageCodeRegistry}, never
 * derived from the name.
 *
 * <h3>Message Code Ranges</h3>
 * <ol>
 * <li><b>0-54</b>: Core protocol messages - defined in {@link GNetCoreMessageCode}</li>
 * <li><b>60-64</b>: Lastprice/intraday price exchange</li>
 * <li><b>70-79</b>: Exchange configuration synchronization</li>
 * <li><b>80-89</b>: Historical price exchange (including coverage queries)</li>
 * <li><b>90-95</b>: Security metadata lookup (including batch)</li>
 * </ol>
 *
 * @see GNetCoreMessageCode for core protocol messages (0-54)
 * @see GTProtocolDescriptors for what each of these codes means
 * @see GTNetMessage for message storage and threading
 */
public enum GTNetMessageCodeType implements GTNetMessageCode {

  // Lastprice exchange messages (60-69)
  /** Request intraday prices from remote server, includes current local prices for bidirectional exchange */
  GT_NET_LASTPRICE_EXCHANGE_SEL_C((byte) 60),
  /** Response containing intraday prices that are more current than those in the request */
  GT_NET_LASTPRICE_EXCHANGE_RESPONSE_S((byte) 61),
  /** Push intraday prices to remote server without requesting prices back */
  GT_NET_LASTPRICE_PUSH_SEL_C((byte) 62),
  /** Acknowledge receipt of pushed prices with count of accepted updates */
  GT_NET_LASTPRICE_PUSH_ACK_S((byte) 63),
  /** Response when the request exceeds the configured max_limit for instruments */
  GT_NET_LASTPRICE_MAX_LIMIT_EXCEEDED_S((byte) 64),

  // Exchange sync messages (70-79)
  /** Request exchange configuration sync from remote server, includes local changed entries since last sync */
  GT_NET_EXCHANGE_SYNC_SEL_RR_C((byte) 70),
  /** Response containing remote's changed exchange entries for bidirectional sync */
  GT_NET_EXCHANGE_SYNC_RESPONSE_S((byte) 71),

  // Historyquote exchange messages (80-89)
  /** Request historical prices from remote server for date range, includes local date coverage info */
  GT_NET_HISTORYQUOTE_EXCHANGE_SEL_C((byte) 80),
  /** Response containing historical prices for requested dates */
  GT_NET_HISTORYQUOTE_EXCHANGE_RESPONSE_S((byte) 81),
  /** Push historical prices to remote server without requesting prices back */
  GT_NET_HISTORYQUOTE_PUSH_SEL_C((byte) 82),
  /** Acknowledge receipt of pushed historical prices with count of accepted updates */
  GT_NET_HISTORYQUOTE_PUSH_ACK_S((byte) 83),
  /** Response when the request exceeds the configured max_limit for instruments or date range */
  GT_NET_HISTORYQUOTE_MAX_LIMIT_EXCEEDED_S((byte) 84),
  /** Request coverage metadata (min/max dates) for instruments without fetching actual price data */
  GT_NET_HISTORYQUOTE_COVERAGE_SEL_C((byte) 85),
  /** Response containing coverage date ranges per instrument for peer selection */
  GT_NET_HISTORYQUOTE_COVERAGE_RESPONSE_S((byte) 86),

  // Security metadata lookup messages (90-95)
  /** Request security metadata by ISIN, currency, and/or ticker symbol from remote server */
  GT_NET_SECURITY_LOOKUP_SEL_C((byte) 90),
  /** Response containing matching security metadata */
  GT_NET_SECURITY_LOOKUP_RESPONSE_S((byte) 91),
  /** Response when no matching security is found */
  GT_NET_SECURITY_LOOKUP_NOT_FOUND_S((byte) 92),
  /** Response when the lookup request is rejected (rate limit, permission, etc.) */
  GT_NET_SECURITY_LOOKUP_REJECTED_S((byte) 93),
  /** Request security metadata for multiple securities in a batch from remote server */
  GT_NET_SECURITY_BATCH_LOOKUP_SEL_C((byte) 94),
  /** Response containing matching security metadata for batch lookup, grouped by query index */
  GT_NET_SECURITY_BATCH_LOOKUP_RESPONSE_S((byte) 95);

  private final byte value;

  private GTNetMessageCodeType(byte value) {
    this.value = value;
  }

  public byte getValue() {
    return this.value;
  }

  /**
   * Unified lookup for message codes by byte value. Checks the application codes (60+) first and falls back to the core
   * protocol codes (0-54).
   *
   * @param value the byte value to look up
   * @return the corresponding GTNetMessageCode, or null if not found
   */
  public static GTNetMessageCode getMessageCodeByValue(byte value) {
    for (GTNetMessageCodeType appCode : values()) {
      if (appCode.getValue() == value) {
        return appCode;
      }
    }
    return GNetCoreMessageCode.getByValue(value);
  }
}
