package grafiosch.entities;

import grafiosch.gtnet.GTNetMessageCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Defines automatic response templates for incoming GTNet messages requiring replies.
 *
 * Each row represents one conditional response option for a specific request message type. Multiple rows with the same
 * request_msg_code form a priority-ordered chain of conditions. The priority field determines the evaluation order -
 * lower values are evaluated first. When a condition matches, the corresponding response is sent and evaluation stops.
 *
 * Conditions are evaluated using EvalEx expressions and can reference variables such as:
 * <ul>
 *   <li>Time of day and day of week</li>
 *   <li>Current request load and daily counters</li>
 *   <li>Requester's domain information and timezone</li>
 *   <li>Payload values from the incoming message</li>
 * </ul>
 *
 * If no condition matches across all priority levels, the message waits for manual admin review.
 *
 * The {@code waitDaysApply} field enforces a cooling-off period after negative responses, preventing immediate
 * re-requests from the same domain.
 *
 * @see GTNetMessageCode for message types that may require automated responses
 */
@Entity
@Table(name = GTNetMessageAnswer.TABNAME)
@Schema(description = """
    Defines automatic response templates for incoming GTNet messages requiring replies. Each row represents one
    conditional response option, with multiple rows per request type forming a priority-ordered chain. Conditions
    are evaluated using EvalEx expressions referencing time, request load, requester information, and payload values.
    If no condition matches, the message waits for manual review.""")
public class GTNetMessageAnswer extends BaseID<Integer> {
  public static final String TABNAME = "gt_net_message_answer";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Schema(description = "Auto-generated unique identifier for this response rule.")
  @Column(name = "id_gt_net_message_answer")
  private Integer idGtNetMessageAnswer;

  @Schema(description = """
      The incoming request message code this answer template applies to. Uses values from GTNetMessageCode interface
      implementations, such as GT_NET_FIRST_HANDSHAKE_SEL_RR_S or GT_NET_UPDATE_SERVERLIST_SEL_RR_C. Multiple rows
      can share the same request_msg_code, each with a different priority.""")
  @Column(name = "request_msg_code", nullable = false)
  private byte requestMsgCode;

  @Schema(description = """
      The response message code to send if this rule's condition matches. Uses values from GTNetMessageCode interface
      implementations, typically an ACCEPT or REJECT variant such as GT_NET_FIRST_HANDSHAKE_ACCEPT_S or
      GT_NET_FIRST_HANDSHAKE_REJECT_S.""")
  @Column(name = "response_msg_code", nullable = false)
  private byte responseMsgCode;

  @Schema(description = """
      Priority order for evaluating this rule within its request_msg_code group. Lower values are evaluated first.
      When multiple rules exist for the same request type, they form a priority chain - the first matching condition
      determines the response.""")
  @Column(name = "priority", nullable = false)
  private byte priority;

  @Schema(description = """
      EvalEx expression for evaluating whether this response should be sent. Can reference variables like 'hour',
      'dayOfWeek', 'dailyCount', 'requesterTimezone', and payload fields. Null or empty string means unconditional
      match (always send this response if reached in the priority chain).""")
  @Column(name = "response_msg_conditional")
  private String responseMsgConditional;

  @Schema(description = """
      Optional human-readable message to include with the response. Typically used to provide context for the
      decision, such as 'Automatically approved during business hours' or 'Capacity limit reached'.""")
  @Column(name = "response_msg_message")
  private String responseMsgMessage;

  @Schema(description = """
      Cooling-off period in days after a negative/rejection response. If set, the requesting domain must wait this
      many days before submitting another request of the same type. Helps prevent request spam and gives
      administrators time to review persistent requesters. 0 means no waiting period.""")
  @Min(value = 0)
  @Max(value = 9999)
  @Column(name = "wait_days_apply", nullable = false)
  private Short waitDaysApply;

  public Integer getIdGtNetMessageAnswer() {
    return idGtNetMessageAnswer;
  }

  public void setIdGtNetMessageAnswer(Integer idGtNetMessageAnswer) {
    this.idGtNetMessageAnswer = idGtNetMessageAnswer;
  }

  /**
   * Gets the raw byte value of the request message code.
   *
   * @return the request message code byte value
   */
  public byte getRequestMsgCodeValue() {
    return requestMsgCode;
  }

  /**
   * Sets the request message code using a GTNetMessageCode interface.
   *
   * @param requestMsgCode the message code implementing GTNetMessageCode
   */
  public void setRequestMsgCode(GTNetMessageCode requestMsgCode) {
    this.requestMsgCode = requestMsgCode.getValue();
  }

  /**
   * Sets the request message code using a raw byte value.
   *
   * @param requestMsgCode the request message code byte value
   */
  public void setRequestMsgCodeValue(byte requestMsgCode) {
    this.requestMsgCode = requestMsgCode;
  }

  /**
   * Gets the raw byte value of the response message code.
   *
   * @return the response message code byte value
   */
  public byte getResponseMsgCodeValue() {
    return responseMsgCode;
  }

  /**
   * Sets the response message code using a GTNetMessageCode interface.
   *
   * @param responseMsgCode the message code implementing GTNetMessageCode
   */
  public void setResponseMsgCode(GTNetMessageCode responseMsgCode) {
    this.responseMsgCode = responseMsgCode.getValue();
  }

  /**
   * Sets the response message code using a raw byte value.
   *
   * @param responseMsgCode the response message code byte value
   */
  public void setResponseMsgCodeValue(byte responseMsgCode) {
    this.responseMsgCode = responseMsgCode;
  }

  public byte getPriority() {
    return priority;
  }

  public void setPriority(byte priority) {
    this.priority = priority;
  }

  public String getResponseMsgConditional() {
    return responseMsgConditional;
  }

  public void setResponseMsgConditional(String responseMsgConditional) {
    this.responseMsgConditional = responseMsgConditional;
  }

  public String getResponseMsgMessage() {
    return responseMsgMessage;
  }

  public void setResponseMsgMessage(String responseMsgMessage) {
    this.responseMsgMessage = responseMsgMessage;
  }

  public Short getWaitDaysApply() {
    return waitDaysApply;
  }

  public void setWaitDaysApply(Short waitDaysApply) {
    this.waitDaysApply = waitDaysApply;
  }

  @Override
  public Integer getId() {
    return idGtNetMessageAnswer;
  }

}
