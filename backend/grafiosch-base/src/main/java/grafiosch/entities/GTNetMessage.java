package grafiosch.entities;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.common.PropertyOnlyCreation;
import grafiosch.common.PropertySelectiveUpdatableOrWhenNull;
import grafiosch.gtnet.DeliveryStatus;
import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.GTNetMessageCode;
import grafiosch.gtnet.MessageVisibility;
import grafiosch.gtnet.SendReceivedType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Records messages exchanged between GT-Network instances for handshake, data requests, and notifications.
 *
 * The messaging model supports both point-to-point and broadcast communication patterns:
 * <ul>
 * <li>Point-to-point: Messages sent to a specific remote domain (e.g., handshake, data requests)</li>
 * <li>Broadcast: Messages sent to all domains matching certain criteria (e.g., maintenance announcements)</li>
 * </ul>
 *
 * Message threading is supported via {@code replyTo} (local reply chain) and {@code idSourceGtNetMessage} (remote
 * message correlation). The combination enables both peers to track conversation threads.
 *
 * The {@code gtNetMessageParamMap} contains typed parameters specific to each message code, enabling extensible
 * payloads without schema changes.
 *
 * @see GTNetMessageCode for message code interface
 * @see SendReceivedType for message direction tracking
 */
@Entity
@Table(name = GTNetMessage.TABNAME)
@Schema(description = """
    Records messages exchanged between GT-Network instances for handshake, data requests, and notifications.
    Each message is stored at both sender and receiver with appropriate direction flags. Supports conversation
    threading via replyTo (local) and idSourceGtNetMessage (remote correlation). The gtNetMessageParamMap
    contains typed parameters specific to each message code, enabling extensible payloads. Message codes ending
    with '_C' are client-initiated; codes ending with '_S' are server responses; codes containing '_ALL_' are
    broadcast to multiple recipients.""")
public class GTNetMessage extends BaseID<Integer> {

  public static final String TABNAME = "gt_net_message";
  public static final String GT_NET_MESSAGE_PARAM = "gt_net_message_param";

  /**
   * Pluggable resolver for converting message code byte values to their string names. By default, resolves only core
   * protocol codes (0-54). Application modules can set a custom resolver that also handles app-specific codes (60+).
   */
  private static volatile Function<Byte, String> messageCodeResolver = value -> {
    GTNetMessageCode code = GNetCoreMessageCode.getByValue(value);
    return code != null ? code.name() : null;
  };

  /**
   * Sets a custom message code resolver. Call this during application startup to enable resolution of app-specific
   * message codes.
   *
   * @param resolver function that converts byte value to enum name string
   */
  public static void setMessageCodeResolver(Function<Byte, String> resolver) {
    messageCodeResolver = resolver;
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_gt_net_message")
  private Integer idGtNetMessage;

  @Schema(description = """
      Foreign key to GTNet. For sent messages (SEND), this is the ID of the target remote domain. For received
      messages (RECEIVED), this is the ID of the source remote domain. This bidirectional interpretation enables
      grouping messages by conversation partner.""")
  @Column(name = "id_gt_net")
  private Integer idGtNet;

  @Schema(description = """
      UTC timestamp when this message was created. All timestamps in GTNet use UTC to ensure consistent ordering
      across time zones and enable proper correlation of request-response pairs.""")
  @Column(name = "timestamp")
  @Temporal(TemporalType.TIMESTAMP)
  private Date timestamp;

  @Schema(description = """
      Direction indicator for the message. Uses SendReceivedType enum: SEND (0) for outgoing messages, RECEIVED (1)
      for incoming messages, ANSWER (2) for transient response messages that may not be persisted. This field enables
      filtering and display of message threads from the local perspective.""")
  @Column(name = "send_recv")
  private byte sendRecv;

  @Schema(description = """
      The message ID from the remote system, used for reply correlation. When responding to a received message,
      this field stores the remote's idGtNetMessage so the remote can match our response to its original request.
      Enables conversation threading across system boundaries.""")
  @PropertyOnlyCreation
  @Column(name = "id_source_gt_net_message")
  private Integer idSourceGtNetMessage;

  @Schema(description = """
      Local reply chain reference. Points to a previous local GTNetMessage that this message is replying to.
      Used in conjunction with idSourceGtNetMessage for full conversation threading. Null for initial messages
      that don't reply to anything.""")
  @PropertyOnlyCreation
  @Column(name = "reply_to")
  private Integer replyTo;

  @Schema(description = """
      Reference to the original announcement message that this cancellation message refers to. Only used for
      cancellation message codes (GT_NET_MAINTENANCE_CANCEL_ALL_C and GT_NET_OPERATION_DISCONTINUED_CANCEL_ALL_C).
      Enables the GTNetFutureMessageDeliveryTask to find recipients who received the original announcement and
      need the cancellation, vs. recipients who haven't received the original and should receive neither.""")
  @PropertyOnlyCreation
  @Column(name = "id_original_message")
  private Integer idOriginalMessage;

  @Schema(description = """
      The message type code. Determines the message semantics and expected payload structure. Codes ending with '_C'
      are client-initiated requests; codes ending with '_S' are server responses; codes containing '_ALL_' are
      broadcast to multiple recipients.""")
  @PropertySelectiveUpdatableOrWhenNull
  @Column(name = "message_code")
  private byte messageCode;

  @Schema(description = """
      Optional free-text message content. Typically used for human-readable notes added by administrators, such as
      reasons for rejection or additional context. Displayed alongside the structured message parameters in the UI.""")
  @PropertyOnlyCreation
  @Column(name = "message")
  private String message;

  @Schema(description = """
      Visibility level for this message. Controls who can see the message:
      ALL_USERS (0) = visible to everyone, ADMIN_ONLY (1) = visible only to administrators.
      Thread visibility rules: replies to ADMIN_ONLY threads are forced to ADMIN_ONLY,
      replies to ALL_USERS threads can be either visibility level.""")
  @PropertyOnlyCreation
  @Column(name = "visibility")
  private byte visibility = MessageVisibility.ALL_USERS.getValue();

  @Schema(description = """
      Error message code for failed operations. Contains the i18n key (not yet translated) that describes what went
      wrong. The frontend translates this code to the user's language for display. Null when the message represents
      a successful operation.""")
  @Column(name = "error_msg_code")
  private String errorMsgCode;

  @Schema(description = """
      Read status flag for inbox management. Set to true when the administrator has viewed/acknowledged this message.
      Used to highlight unread messages in the UI and track which messages require attention.""")
  @PropertyAlwaysUpdatable
  @Column(name = "has_been_read")
  private boolean hasBeenRead;

  @Schema(description = """
      Delivery status for outgoing messages. Tracks whether the message was successfully transmitted to the remote
      domain. PENDING (0) = queued or retries possible, DELIVERED (1) = successfully sent, FAILED (2) = all retries
      exhausted. For received messages (sendRecv = RECEIVED), this field is not applicable.""")
  @Column(name = "delivery_status")
  private byte deliveryStatus = DeliveryStatus.PENDING.getValue();

  @Schema(description = """
      Cooling-off period in days after a negative/rejection response. If set, the requesting domain must wait this
      many days before submitting another request of the same type. Helps prevent request spam and gives
      administrators time to review persistent requesters. 0 means no waiting period.""")
  @Min(value = 0)
  @Max(value = 9999)
  @Column(name = "wait_days_apply", nullable = false)
  private Short waitDaysApply = 0;

  @ElementCollection(fetch = FetchType.EAGER)
  @MapKeyColumn(name = "param_name")
  @CollectionTable(name = GT_NET_MESSAGE_PARAM, joinColumns = @JoinColumn(name = "id_gt_net_message"))
  private Map<String, GTNetMessageParam> gtNetMessageParamMap = new HashMap<>();

  @Schema(description = """
      Transient field indicating whether this message can be deleted. Computed by the backend based on message type,
      delivery status, and whether the message is still awaiting a response. Messages with replyTo set (response messages)
      should not show a checkbox as they are cascade-deleted with their parent request.""")
  @Transient
  private boolean canDelete;

  public GTNetMessage() {
  }

  public GTNetMessage(Integer idGtNet, Date timestamp, byte sendRecv, Integer replyTo, byte messageCode, String message,
      Map<String, GTNetMessageParam> gtNetMessageParamMap) {
    super();
    this.idGtNet = idGtNet;
    this.timestamp = timestamp;
    this.sendRecv = sendRecv;
    this.replyTo = replyTo;
    this.messageCode = messageCode;
    this.message = message;
    this.gtNetMessageParamMap = gtNetMessageParamMap;
  }

  public Integer getIdGtNetMessage() {
    return idGtNetMessage;
  }

  public void setIdGtNetMessage(Integer idGtNetMessage) {
    this.idGtNetMessage = idGtNetMessage;
  }

  public Integer getIdGtNet() {
    return idGtNet;
  }

  public void setIdGtNet(Integer idGtNet) {
    this.idGtNet = idGtNet;
  }

  public Date getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(Date timestamp) {
    this.timestamp = timestamp;
  }

  public SendReceivedType getSendRecv() {
    return SendReceivedType.getSendReceivedType(sendRecv);
  }

  public void setSendRecv(SendReceivedType sendReceivedType) {
    this.sendRecv = sendReceivedType.getValue();
  }

  public Integer getIdSourceGtNetMessage() {
    return idSourceGtNetMessage;
  }

  public void setIdSourceGtNetMessage(Integer idSourceGtNetMessage) {
    this.idSourceGtNetMessage = idSourceGtNetMessage;
  }

  public Integer getReplyTo() {
    return replyTo;
  }

  public void setReplyTo(Integer replyTo) {
    this.replyTo = replyTo;
  }

  public Integer getIdOriginalMessage() {
    return idOriginalMessage;
  }

  public void setIdOriginalMessage(Integer idOriginalMessage) {
    this.idOriginalMessage = idOriginalMessage;
  }

  /**
   * Gets the raw byte value of the message code.
   *
   * @return the message code byte value
   */
  public byte getMessageCodeValue() {
    return messageCode;
  }

  /**
   * Gets the message code as a string enum name for JSON serialization. Uses the configured resolver to convert the
   * byte value to the corresponding enum constant name (e.g., "GT_NET_MAINTENANCE_ALL_C"). This transient field enables
   * the frontend to display translated message code labels.
   *
   * @return the enum constant name, or null if the code is not recognized
   */
  @Transient
  @Schema(description = """
      The message code as a string enum name for display. Converted from the internal byte value to enable frontend
      translation lookup. Examples: GT_NET_PING, GT_NET_MAINTENANCE_ALL_C, GT_NET_LASTPRICE_EXCHANGE_SEL_C.""")
  public String getMessageCode() {
    return messageCodeResolver.apply(messageCode);
  }

  /**
   * Sets the message code using a GTNetMessageCode interface.
   *
   * @param messageCode the message code implementing GTNetMessageCode
   */
  public void setMessageCode(GTNetMessageCode messageCode) {
    this.messageCode = messageCode.getValue();
  }

  /**
   * Sets the message code using a raw byte value.
   *
   * @param messageCode the message code byte value
   */
  public void setMessageCodeValue(byte messageCode) {
    this.messageCode = messageCode;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  /**
   * Gets the visibility level for this message.
   *
   * @return the MessageVisibility enum value
   */
  public MessageVisibility getVisibility() {
    return MessageVisibility.getByValue(visibility);
  }

  /**
   * Sets the visibility level for this message.
   *
   * @param visibility the MessageVisibility enum value
   */
  public void setVisibility(MessageVisibility visibility) {
    this.visibility = visibility.getValue();
  }

  /**
   * Gets the raw byte value of the visibility.
   *
   * @return the visibility byte value
   */
  public byte getVisibilityValue() {
    return visibility;
  }

  /**
   * Sets the visibility using a raw byte value.
   *
   * @param visibility the visibility byte value
   */
  public void setVisibilityValue(byte visibility) {
    this.visibility = visibility;
  }

  public String getErrorMsgCode() {
    return errorMsgCode;
  }

  public void setErrorMsgCode(String errorMsgCode) {
    this.errorMsgCode = errorMsgCode;
  }

  public Map<String, GTNetMessageParam> getGtNetMessageParamMap() {
    return gtNetMessageParamMap;
  }

  public void setGtNetMessageParamMap(Map<String, GTNetMessageParam> gtNetMessageParamMap) {
    this.gtNetMessageParamMap = gtNetMessageParamMap;
  }

  public boolean isHasBeenRead() {
    return hasBeenRead;
  }

  public void setHasBeenRead(boolean hasBeenRead) {
    this.hasBeenRead = hasBeenRead;
  }

  public DeliveryStatus getDeliveryStatus() {
    return DeliveryStatus.getDeliveryStatus(deliveryStatus);
  }

  public void setDeliveryStatus(DeliveryStatus deliveryStatus) {
    this.deliveryStatus = deliveryStatus.getValue();
  }

  @Override
  public Integer getId() {
    return idGtNetMessage;
  }

  public Short getWaitDaysApply() {
    return waitDaysApply;
  }

  public void setWaitDaysApply(Short waitDaysApply) {
    this.waitDaysApply = waitDaysApply;
  }

  public boolean isCanDelete() {
    return canDelete;
  }

  public void setCanDelete(boolean canDelete) {
    this.canDelete = canDelete;
  }

  @Embeddable
  public static class GTNetMessageParam extends BaseParam {
    public GTNetMessageParam() {
    }

    public GTNetMessageParam(String paramValue) {
      this.paramValue = paramValue;
    }
  }

  public void checkAndUpdateSomeValues() {
    waitDaysApply = waitDaysApply == null ? 0 : waitDaysApply;
    // Remove entries with null paramValue as the database doesn't allow nulls
    if (gtNetMessageParamMap != null) {
      gtNetMessageParamMap.entrySet().removeIf(e -> e.getValue() == null || e.getValue().getParamValue() == null);
    }
  }

  @Override
  public String toString() {
    String paramMapStr = (gtNetMessageParamMap != null) ? gtNetMessageParamMap.entrySet().stream()
        .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", ", "{", "}")) : "null";

    return "GTNetMessage [idGtNetMessage=" + idGtNetMessage + ", idGtNet=" + idGtNet + ", timestamp=" + timestamp
        + ", sendRecv=" + sendRecv + ", idSourceGtNetMessage=" + idSourceGtNetMessage + ", replyTo=" + replyTo
        + ", idOriginalMessage=" + idOriginalMessage + ", messageCode=" + messageCode + ", message=" + message
        + ", visibility=" + visibility + ", errorMsgCode=" + errorMsgCode + ", hasBeenRead=" + hasBeenRead
        + ", deliveryStatus=" + deliveryStatus + ", waitDaysApply=" + waitDaysApply
        + ", gtNetMessageParamMap=" + paramMapStr + "]";
  }
}
