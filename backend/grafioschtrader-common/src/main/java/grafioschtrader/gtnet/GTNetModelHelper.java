package grafioschtrader.gtnet;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import grafiosch.dynamic.model.ClassDescriptorInputAndShow;
import grafiosch.dynamic.model.DynamicModelHelper;
import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.gtnet.model.msg.DataRequestMsg;
import grafioschtrader.gtnet.model.msg.DiscontinuedMsg;
import grafioschtrader.gtnet.model.msg.FirstHandshakeMsg;
import grafioschtrader.gtnet.model.msg.MaintenanceMsg;
import grafioschtrader.gtnet.model.msg.RevokeMsg;
import grafioschtrader.gtnet.model.msg.UpdateServerlistRequestMsg;

/**
 * Central registry mapping GTNet message codes to their payload model classes and metadata.
 *
 * This class serves two primary purposes:
 * <ol>
 *   <li><b>Form Generation</b>: Provides model class metadata to the frontend via
 *       {@link #getAllFormDefinitionsWithClass()}, enabling dynamic form generation for each message type</li>
 *   <li><b>Message Processing</b>: Maps incoming message codes to their expected payload model class
 *       via {@link #getMsgClassByMessageCode(GTNetMessageCodeType)}</li>
 * </ol>
 *
 * The registry uses static initialization to define which model class (e.g., {@code FirstHandshakeMsg},
 * {@code MaintenanceMsg}) corresponds to each message code, and whether a response is expected.
 *
 * <h3>Naming Conventions</h3>
 * Message code names follow patterns that indicate behavior:
 * <ul>
 *   <li>{@code _C} suffix: Client-initiated, shown in UI for user selection</li>
 *   <li>{@code _S} suffix: Server response, not shown in UI initiation dialogs</li>
 *   <li>{@code _ALL_} contains: Broadcast to multiple recipients</li>
 *   <li>{@code _SEL_} contains: Targeted at a selected single recipient</li>
 * </ul>
 *
 * @see GTNetMessageCodeType for the complete list of message codes
 * @see GTNetMessage for message storage
 */
public abstract class GTNetModelHelper {

  /** Pattern in message code name indicating broadcast to all applicable domains. */
  public final static String MESSAGE_TO_ALL = "_ALL_";

  /** Pattern in message code name indicating client-initiated (UI-triggerable) messages. */
  public final static String IS_USER_REQUEST_STR = "_C";

  /** Registry mapping message codes to their model class and response expectation. */
  private static Map<GTNetMessageCodeType, GTNetMsgRequest> msgFormMap;

  static {
    msgFormMap = new HashMap<>();
    msgFormMap.put(GTNetMessageCodeType.GT_NET_FIRST_HANDSHAKE_SEL_RR_S, new GTNetMsgRequest(FirstHandshakeMsg.class, true, (byte) 1));
    msgFormMap.put(GTNetMessageCodeType.GT_NET_UPDATE_SERVERLIST_SEL_RR_C,
        new GTNetMsgRequest(UpdateServerlistRequestMsg.class, true, (byte) 1));
    msgFormMap.put(GTNetMessageCodeType.GT_NET_MAINTENANCE_ALL_C, new GTNetMsgRequest(MaintenanceMsg.class, false, (byte) 10));

    // Unified data exchange messages
    msgFormMap.put(GTNetMessageCodeType.GT_NET_DATA_REQUEST_SEL_RR_C,
        new GTNetMsgRequest(DataRequestMsg.class, true, (byte) 1));
    msgFormMap.put(GTNetMessageCodeType.GT_NET_DATA_REVOKE_SEL_C, new GTNetMsgRequest(RevokeMsg.class, false, (byte) 1));

    // Server status announcements - no model, no response expected
    msgFormMap.put(GTNetMessageCodeType.GT_NET_OFFLINE_ALL_C, new GTNetMsgRequest(null, false, (byte) 1));
    msgFormMap.put(GTNetMessageCodeType.GT_NET_ONLINE_ALL_C, new GTNetMsgRequest(null, false, (byte) 1));
    msgFormMap.put(GTNetMessageCodeType.GT_NET_BUSY_ALL_C, new GTNetMsgRequest(null, false, (byte) 1));
    msgFormMap.put(GTNetMessageCodeType.GT_NET_RELEASED_BUSY_ALL_C, new GTNetMsgRequest(null, false, (byte) 1));
    msgFormMap.put(GTNetMessageCodeType.GT_NET_OPERATION_DISCONTINUED_ALL_C, new GTNetMsgRequest(DiscontinuedMsg.class, false, (byte) 1));

    // Cancel announcements - no model, no response expected
    msgFormMap.put(GTNetMessageCodeType.GT_NET_MAINTENANCE_CANCEL_ALL_C, new GTNetMsgRequest(null, false, (byte) 1));
    msgFormMap.put(GTNetMessageCodeType.GT_NET_OPERATION_DISCONTINUED_CANCEL_ALL_C, new GTNetMsgRequest(null, false, (byte) 1));
  }

  /**
   * Returns form definitions for all client-initiatable message types.
   *
   * Filters the registry to include only message codes ending with "_C" (client-initiated) that have
   * a non-null model class. The returned map is consumed by the frontend to dynamically build input
   * forms for each message type.
   *
   * @return map of message code to form descriptor, for use by GTNetMessageEditComponent
   */
  public static Map<GTNetMessageCodeType, ClassDescriptorInputAndShow> getAllFormDefinitionsWithClass() {
    return msgFormMap.entrySet().stream()
        .filter(e -> e.getKey().name().endsWith(IS_USER_REQUEST_STR) && e.getValue().model != null).collect(Collectors
            .toMap(Map.Entry::getKey, e -> DynamicModelHelper.getFormDefinitionOfModelClass(e.getValue().model)));
  }

  /**
   * Looks up the model class and response expectation for a given message code.
   *
   * Used during message processing to:
   * <ul>
   *   <li>Deserialize incoming message parameters into the correct POJO</li>
   *   <li>Determine whether to wait for a synchronous response</li>
   * </ul>
   *
   * @param gtNetMessageCodeType the message code to look up
   * @return the GTNetMsgRequest with model class and response flag, or null if not registered
   */
  public static GTNetMsgRequest getMsgClassByMessageCode(GTNetMessageCodeType gtNetMessageCodeType) {
    return msgFormMap.get(gtNetMessageCodeType);
  }

  /**
   * Metadata container for a registered message type.
   *
   * Pairs a model class (POJO for the message payload) with a flag indicating whether the sender
   * should expect and wait for a synchronous response.
   */
  public static class GTNetMsgRequest {
    /** The POJO class representing the message payload structure. Null for messages with no parameters. */
    public Class<?> model;

    /** True if the sender should wait for a synchronous response after sending this message type. */
    public boolean responseExpected;
    
    /** Certain messages should be sent repeatedly until they are received by the recipient or until the limit for transmission attempts has been reached.  */
    public byte repeatSendAsMany;

    public GTNetMsgRequest(Class<?> model, boolean responseExpected, byte repeatSendAsMany) {
      this.model = model;
      this.responseExpected = responseExpected;
      this.repeatSendAsMany = repeatSendAsMany;
    }
  }

}
