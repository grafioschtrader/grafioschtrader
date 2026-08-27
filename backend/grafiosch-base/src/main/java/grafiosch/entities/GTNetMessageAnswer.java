package grafiosch.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;

import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.GTNetMessageCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
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
 * <li>Time of day and day of week</li>
 * <li>Current request load and daily counters</li>
 * <li>Requester's domain information and timezone</li>
 * <li>Payload values from the incoming message</li>
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

  /**
   * Message codes a JSON enum name can be resolved against, seeded with the core protocol codes.
   *
   * <p>
   * The two code columns are bytes, so nothing in the entity itself can turn the name the edit dialog sends into a
   * value. {@code GTNetMessageCodeRegistry} knows every registered code but is a Spring bean in
   * {@code grafiosch-server-base}, out of reach of an entity here - the same split {@code GTNetEntity} solves with a
   * static array for its exchange kinds. Seeding with {@link GNetCoreMessageCode} means a host that adds no codes of
   * its own needs no registration at all.
   * </p>
   */
  @Transient
  private static volatile GTNetMessageCode[] messageCodes = GNetCoreMessageCode.values();

  /**
   * Adds the application's message codes to those a JSON enum name is resolved against. Call during startup, before the
   * first {@code GTNetMessageAnswer} is deserialized; the core codes are always present.
   *
   * @param codes the application's message code enum values
   */
  public static void registerMessageCodes(GTNetMessageCode[] codes) {
    GTNetMessageCode[] merged = new GTNetMessageCode[messageCodes.length + codes.length];
    System.arraycopy(messageCodes, 0, merged, 0, messageCodes.length);
    System.arraycopy(codes, 0, merged, messageCodes.length, codes.length);
    messageCodes = merged;
  }

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
  @JsonIgnore
  public byte getRequestMsgCodeValue() {
    return requestMsgCode;
  }

  /**
   * Sets the request message code using a GTNetMessageCode interface.
   *
   * @param requestMsgCode the message code implementing GTNetMessageCode
   */
  @JsonIgnore
  public void setRequestMsgCode(GTNetMessageCode requestMsgCode) {
    this.requestMsgCode = requestMsgCode.getValue();
  }

  /**
   * Sets the request message code using a raw byte value.
   *
   * @param requestMsgCode the request message code byte value
   */
  @JsonIgnore
  public void setRequestMsgCodeValue(byte requestMsgCode) {
    this.requestMsgCode = requestMsgCode;
  }

  /**
   * Serializes the request message code as its byte value.
   *
   * @return the request message code byte value
   */
  @JsonProperty("requestMsgCode")
  public byte getRequestMsgCode() {
    return requestMsgCode;
  }

  /**
   * Sets the request message code from JSON, accepting a numeric value or an enum name.
   *
   * @param value the message code as a Number (byte value) or String (enum constant name)
   * @throws IllegalArgumentException if the value is neither, or the name is not registered
   */
  @JsonSetter("requestMsgCode")
  public void setRequestMsgCode(Object value) {
    this.requestMsgCode = resolveMessageCode("requestMsgCode", value);
  }

  /**
   * Gets the raw byte value of the response message code.
   *
   * @return the response message code byte value
   */
  @JsonIgnore
  public byte getResponseMsgCodeValue() {
    return responseMsgCode;
  }

  /**
   * Sets the response message code using a GTNetMessageCode interface.
   *
   * @param responseMsgCode the message code implementing GTNetMessageCode
   */
  @JsonIgnore
  public void setResponseMsgCode(GTNetMessageCode responseMsgCode) {
    this.responseMsgCode = responseMsgCode.getValue();
  }

  /**
   * Sets the response message code using a raw byte value.
   *
   * @param responseMsgCode the response message code byte value
   */
  @JsonIgnore
  public void setResponseMsgCodeValue(byte responseMsgCode) {
    this.responseMsgCode = responseMsgCode;
  }

  /**
   * Serializes the response message code as its byte value.
   *
   * @return the response message code byte value
   */
  @JsonProperty("responseMsgCode")
  public byte getResponseMsgCode() {
    return responseMsgCode;
  }

  /**
   * Sets the response message code from JSON, accepting a numeric value or an enum name.
   *
   * @param value the message code as a Number (byte value) or String (enum constant name)
   * @throws IllegalArgumentException if the value is neither, or the name is not registered
   */
  @JsonSetter("responseMsgCode")
  public void setResponseMsgCode(Object value) {
    this.responseMsgCode = resolveMessageCode("responseMsgCode", value);
  }

  /**
   * Resolves a JSON value to a message code byte.
   *
   * <p>
   * Both fields are declared as {@code byte} but named after {@link GTNetMessageCode}, which is an interface - Jackson
   * can neither construct it on the way in nor find a property for it on the way out. The edit dialog sends the enum
   * constant name, so the name has to be resolved here against the registered codes.
   * </p>
   */
  private static byte resolveMessageCode(String field, Object value) {
    if (value instanceof Number number) {
      return number.byteValue();
    }
    if (value instanceof String name) {
      for (GTNetMessageCode code : messageCodes) {
        if (code.name().equals(name)) {
          return code.getValue();
        }
      }
      throw new IllegalArgumentException("Unknown " + field + ": " + name
          + ". Ensure GTNetMessageAnswer.registerMessageCodes() was called during application startup.");
    }
    throw new IllegalArgumentException(
        field + " must be a number or string, got: " + (value == null ? "null" : value.getClass().getName()));
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
