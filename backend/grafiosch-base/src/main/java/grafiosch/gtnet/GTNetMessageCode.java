package grafiosch.gtnet;

/**
 * Common interface for GTNet message codes.
 *
 * This interface is implemented by both {@code GNetCoreMessageCode} (library message codes 0-54)
 * and application-specific message code enums (e.g., GTNet app codes 60+). It provides a unified
 * way to work with message codes regardless of whether they are core protocol messages or
 * application-specific messages.
 *
 * <h3>Naming Conventions</h3>
 * Message code names follow a structured naming pattern:
 * <ul>
 *   <li><b>Contains _RR_</b>: Message expects a response (RR = Requires Response)</li>
 *   <li><b>Suffix _C</b>: Client-initiated messages (triggered from UI or scheduled jobs)</li>
 *   <li><b>Suffix _S</b>: Server response messages (replies to requests)</li>
 *   <li><b>Contains _SEL_</b>: Targeted at a specific selected remote server</li>
 *   <li><b>Contains _ALL_</b>: Broadcast to all applicable remote servers</li>
 * </ul>
 *
 * @see GNetCoreMessageCode for core protocol message codes
 */
public interface GTNetMessageCode {

  /**
   * Returns the byte value of this message code.
   *
   * @return the byte value used for wire protocol and database storage
   */
  byte getValue();

  /**
   * Returns the name of this message code enum constant.
   *
   * @return the enum constant name
   */
  String name();

  /**
   * Checks if this message code is a request that requires a response.
   *
   * @return true if this is an _RR_ type message code
   */
  default boolean isRequestRequiringResponse() {
    return name().contains("_RR_");
  }

  /**
   * Checks if this message code represents a client-initiated message.
   *
   * @return true if this message ends with _C (client-initiated)
   */
  default boolean isClientInitiated() {
    return name().endsWith("_C");
  }

  /**
   * Checks if this message code represents a server response.
   *
   * @return true if this message ends with _S (server response)
   */
  default boolean isServerResponse() {
    return name().endsWith("_S");
  }

  /**
   * Checks if this message code is a broadcast to all peers.
   *
   * @return true if this message contains _ALL_
   */
  default boolean isBroadcast() {
    return name().contains("_ALL_");
  }

  /**
   * Checks if this message code targets a specific selected peer.
   *
   * @return true if this message contains _SEL_
   */
  default boolean isTargeted() {
    return name().contains("_SEL_");
  }
}
