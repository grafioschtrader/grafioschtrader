package grafiosch.integration.gtnet;

import grafiosch.gtnet.GTNetMessageCode;

/**
 * Message codes that exist only so the two-peer suite can drive a protocol path production code cannot reach.
 *
 * A code is known to {@code GTNetMessageCodeRegistry} without necessarily having a {@code GTNetMessageHandler} bean;
 * the dispatcher answers such a message with the error code {@code NO_HANDLER}. Every production code either has a
 * handler or is a response consumed from the synchronous reply envelope, so the only way to reach that branch without
 * depending on a gap in the protocol is a registered code that deliberately has no handler.
 *
 * The value sits above the application band (60-95) so it can never collide with a core or Grafioschtrader code.
 */
public enum IntegrationTestMessageCode implements GTNetMessageCode {

  /** Registered, never handled - drives the {@code NO_HANDLER} branch of the message dispatcher. */
  GT_NET_INTEGRATION_NO_HANDLER_SEL_C((byte) 120);

  private final byte value;

  private IntegrationTestMessageCode(byte value) {
    this.value = value;
  }

  @Override
  public byte getValue() {
    return this.value;
  }
}
