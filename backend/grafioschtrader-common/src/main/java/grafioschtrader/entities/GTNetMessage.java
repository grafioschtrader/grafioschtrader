package grafioschtrader.entities;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.common.PropertyOnlyCreation;
import grafiosch.common.PropertySelectiveUpdatableOrWhenNull;
import grafiosch.entities.BaseID;
import grafiosch.entities.BaseParam;
import grafioschtrader.gtnet.DeliveryStatus;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.GTNetModelHelper;
import grafioschtrader.gtnet.SendReceivedType;
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
 * payloads without schema changes. Parameter definitions are provided by {@link GTNetModelHelper}.
 *
 * @see GTNetMessageCodeType for available message types and their lifecycle
 * @see GTNetModelHelper for message-to-model mappings
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
      The message type code from GTNetMessageCodeType enum. Determines the message semantics and expected payload
      structure. Codes ending with '_C' are client-initiated requests; codes ending with '_S' are server responses;
      codes containing '_ALL_' are broadcast to multiple recipients.""")
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
  private Short waitDaysApply;

  @ElementCollection(fetch = FetchType.EAGER)
  @MapKeyColumn(name = "param_name")
  @CollectionTable(name = GT_NET_MESSAGE_PARAM, joinColumns = @JoinColumn(name = "id_gt_net_message"))
  private Map<String, GTNetMessageParam> gtNetMessageParamMap = new HashMap<>();

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

  public GTNetMessageCodeType getMessageCode() {
    return GTNetMessageCodeType.getGTNetMessageCodeTypeByValue(messageCode);
  }

  public void setMessageCode(GTNetMessageCodeType gtNetMessageCodeType) {
    this.messageCode = gtNetMessageCodeType.getValue();
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
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

  @Embeddable
  // @MappedSuperclass
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
        + ", errorMsgCode=" + errorMsgCode + ", hasBeenRead=" + hasBeenRead + ", deliveryStatus=" + deliveryStatus
        + ", waitDaysApply=" + waitDaysApply + ", gtNetMessageParamMap=" + paramMapStr + "]";
  }
}
