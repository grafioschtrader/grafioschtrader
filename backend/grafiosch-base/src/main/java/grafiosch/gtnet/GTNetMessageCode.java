package grafiosch.gtnet;

/**
 * Common interface for GTNet message codes.
 *
 * This interface is implemented by both {@code GNetCoreMessageCode} (library message codes 0-54) and
 * application-specific message code enums (e.g., GTNet app codes 60+). It provides a unified way to work with message
 * codes regardless of whether they are core protocol messages or application-specific messages.
 *
 * <h3>Naming Conventions</h3> Message code names carry {@code _SEL_} for a message aimed at one selected peer and
 * {@code _ALL_} for a broadcast, and those two are the only facts still read from a name — they decide who a message is
 * addressed to, which is routing rather than protocol.
 *
 * <p>
 * Nothing else is inferred from a name. The older markers {@code _RR_}, {@code _C} and {@code _S} were unreliable:
 * {@code GT_NET_FIRST_HANDSHAKE_SEL_RR_S} is user-initiated yet ends in {@code _S}. Category, valid answers, payload
 * and rule eligibility are declared in {@code GTNetProtocolDescriptor} and read from {@code GTNetMessageCodeRegistry}.
 * </p>
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
