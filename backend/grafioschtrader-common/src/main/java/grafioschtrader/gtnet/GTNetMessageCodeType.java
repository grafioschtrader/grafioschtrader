package grafioschtrader.gtnet;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.GTNetMessageCode;
import grafiosch.gtnet.GTNetModelHelper;

/**
 * Application-specific message codes for Grafioschtrader GTNet communication.
 *
 * This enum contains only Grafioschtrader-specific message codes (60+) for trading-related
 * functionality like price exchange, history quotes, and security lookup. Core protocol
 * messages (0-54) are defined in {@link GNetCoreMessageCode} in the grafiosch-base module.
 *
 * <h3>Naming Convention</h3>
 * Message codes follow a structured naming pattern that indicates their purpose and behavior:
 * <ul>
 * <li><b>Contains _RR_</b>: Message expects a response (Requires Response)</li>
 * <li><b>Suffix _C</b>: Client-initiated messages (triggered from UI or scheduled jobs)</li>
 * <li><b>Suffix _S</b>: Server response messages (replies to requests)</li>
 * <li><b>Contains _SEL_</b>: Targeted at a specific selected remote server</li>
 * <li><b>Contains _ALL_</b>: Broadcast to all applicable remote servers</li>
 * </ul>
 *
 * <h3>Message Code Ranges</h3>
 * <ol>
 * <li><b>0-54</b>: Core protocol messages - defined in {@link GNetCoreMessageCode}</li>
 * <li><b>60-64</b>: Lastprice/intraday price exchange</li>
 * <li><b>70-79</b>: Exchange configuration synchronization</li>
 * <li><b>80-84</b>: Historical price exchange</li>
 * <li><b>90-95</b>: Security metadata lookup (including batch)</li>
 * </ol>
 *
 * @see GNetCoreMessageCode for core protocol messages (0-54)
 * @see GTNetModelHelper for mapping message codes to payload model classes
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
  
  //Exchange sync messages (70-79)
  /** Request exchange configuration sync from remote server, includes local changed entries since last sync */
  GT_NET_EXCHANGE_SYNC_SEL_RR_C((byte) 70),
  /** Response containing remote's changed exchange entries for bidirectional sync */
  GT_NET_EXCHANGE_SYNC_RESPONSE_S((byte) 71),

  // Historyquote exchange messages (80-84)
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
   * Looks up an app-specific message code (60+) by its byte value.
   *
   * @param value the byte value to look up
   * @return the corresponding GTNetMessageCodeType, or null if not found in app-specific codes
   */
  public static grafioschtrader.gtnet.GTNetMessageCodeType getGTNetMessageCodeTypeByValue(byte value) {
    for (grafioschtrader.gtnet.GTNetMessageCodeType gtNetMessageCodeType : GTNetMessageCodeType.values()) {
      if (gtNetMessageCodeType.getValue() == value) {
        return gtNetMessageCodeType;
      }
    }
    return null;
  }

  /**
   * Unified lookup for message codes by byte value. Checks both app-specific codes (60+)
   * and core protocol codes (0-54).
   *
   * @param value the byte value to look up
   * @return the corresponding GTNetMessageCode, or null if not found
   */
  public static GTNetMessageCode getMessageCodeByValue(byte value) {
    // First check app-specific codes (60+)
    GTNetMessageCodeType appCode = GTNetMessageCodeType.getGTNetMessageCodeTypeByValue(value);
    if (appCode != null) {
      return appCode;
    }
    // Fallback to core protocol codes (0-54)
    return GNetCoreMessageCode.getByValue(value);
  }
  
  
  /**
   * Maps request codes (_RR_) to their valid response codes. Used by the UI to show available response options for
   * unanswered incoming requests. Uses GTNetMessageCode interface to support both core and app-specific codes.
   */
  private static final Map<GTNetMessageCode, List<GTNetMessageCode>> RESPONSE_MAP = Map.of(
      GNetCoreMessageCode.GT_NET_FIRST_HANDSHAKE_SEL_RR_S,
      List.of(GNetCoreMessageCode.GT_NET_FIRST_HANDSHAKE_ACCEPT_S, GNetCoreMessageCode.GT_NET_FIRST_HANDSHAKE_REJECT_S),
      GNetCoreMessageCode.GT_NET_UPDATE_SERVERLIST_SEL_RR_C,
      List.of(GNetCoreMessageCode.GT_NET_UPDATE_SERVERLIST_ACCEPT_S, GNetCoreMessageCode.GT_NET_UPDATE_SERVERLIST_REJECTED_S),
      GNetCoreMessageCode.GT_NET_DATA_REQUEST_SEL_RR_C,
      List.of(GNetCoreMessageCode.GT_NET_DATA_REQUEST_ACCEPT_S, GNetCoreMessageCode.GT_NET_DATA_REQUEST_REJECTED_S),
      GT_NET_EXCHANGE_SYNC_SEL_RR_C, List.of(GT_NET_EXCHANGE_SYNC_RESPONSE_S));

  /**
   * Returns the valid response codes for a given request code.
   *
   * @param requestCode the request message code (must be an _RR_ type)
   * @return list of valid response codes, or empty list if not a request type
   */
  public static List<GTNetMessageCode> getValidResponses(GTNetMessageCode requestCode) {
    return RESPONSE_MAP.getOrDefault(requestCode, Collections.emptyList());
  }

  /**
   * Checks if this message code is a request that requires a response.
   *
   * @return true if this is an _RR_ type message code
   */
  public boolean isRequestRequiringResponse() {
    return this.name().contains("_RR_");
  }
}
