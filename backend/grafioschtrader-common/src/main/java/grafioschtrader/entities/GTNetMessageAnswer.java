package grafioschtrader.entities;

import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.GTNetModelHelper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Defines automatic response templates for incoming GTNet messages requiring replies.
 *
 * Administrators can configure up to three conditional responses per message type, each evaluated using
 * EvalEx expressions. The conditions can reference variables such as:
 * <ul>
 *   <li>Time of day and day of week</li>
 *   <li>Current request load and daily counters</li>
 *   <li>Requester's domain information and timezone</li>
 *   <li>Payload values from the incoming message</li>
 * </ul>
 *
 * Response evaluation order: condition1 is checked first; if true, responseMsgCode1 is sent. If false,
 * condition2 is evaluated, and so on. If no condition matches, the message waits for manual review.
 *
 * The {@code waitDaysApply} field enforces a cooling-off period after negative responses, preventing
 * immediate re-requests from the same domain.
 *
 * Note: The EvalEx evaluation logic in GTNetMessageAnswerJpaRepositoryImpl is currently a placeholder
 * and needs implementation.
 *
 * @see GTNetMessageCodeType for message types that may require automated responses
 * @see GTNetModelHelper for mapping between message codes and their payload models
 */
@Entity
@Table(name = GTNetMessageAnswer.TABNAME)
@Schema(description = """
    Defines automatic response templates for incoming GTNet messages requiring replies. Administrators can configure
    up to three conditional responses per message type, evaluated using EvalEx expressions. Conditions can reference
    time, request load, requester information, and payload values. If no condition matches, the message waits for
    manual review. The waitDaysApply field enforces a cooling-off period after negative responses.""")
public class GTNetMessageAnswer {
  public static final String TABNAME = "gt_net_message_answer";

  @Id
  @Schema(description = """
      The incoming message code this answer template applies to. Acts as the primary key since each request type
      has at most one auto-answer configuration. Uses values from GTNetMessageCodeType enum.""")
  @Column(name = "request_msg_code")
  private byte requestMsgCode;

  @Schema(description = """
      First response option message code. This response is sent if responseMsgConditional1 evaluates to true
      (or is null/empty). Uses values from GTNetMessageCodeType enum, typically an ACCEPT or REJECT variant.""")
  @Column(name = "response_msg_code1")
  private byte responseMsgCode1;

  @Schema(description = """
      EvalEx expression for the first response condition. If this evaluates to true, responseMsgCode1 is sent.
      Can reference variables like 'hour', 'dayOfWeek', 'dailyCount', 'requesterTimezone', and payload fields.
      Null or empty string means always match (unconditional first response).""")
  @Column(name = "response_msg_conditional1")
  private String responseMsgConditional1;

  @Schema(description = """
      Optional human-readable message to include with the first response. Typically used to provide context
      for the decision, such as 'Automatically approved during business hours' or 'Capacity limit reached'.""")
  @Column(name = "response_msg_message1")
  private String responseMsgMessage1;

  @Schema(description = """
      Second response option message code. Evaluated only if responseMsgConditional1 was false. Null if only
      one response option is configured.""")
  @Column(name = "response_msg_code2")
  private Byte responseMsgCode2;

  @Schema(description = """
      EvalEx expression for the second response condition. Evaluated only if the first condition was false.
      If this evaluates to true, responseMsgCode2 is sent. Null or empty means always match at this level.""")
  @Column(name = "response_msg_conditional2")
  private String responseMsgConditional2;

  @Schema(description = "Optional human-readable message to include with the second response.")
  @Column(name = "response_msg_message2")
  private String responseMsgMessage2;

  @Schema(description = """
      Third response option message code. Evaluated only if both previous conditions were false. Null if only
      one or two response options are configured.""")
  @Column(name = "response_msg_code3")
  private Byte responseMsgCode3;

  @Schema(description = """
      EvalEx expression for the third response condition. Evaluated only if both previous conditions were false.
      If this evaluates to true, responseMsgCode3 is sent. If all conditions are false, no automatic response
      is generated and the message waits for manual review.""")
  @Column(name = "response_msg_conditional3")
  private String responseMsgConditional3;

  @Schema(description = "Optional human-readable message to include with the third response.")
  @Column(name = "response_msg_message3")
  private String responseMsgMessage3;

  @Schema(description = """
      Cooling-off period in days after a negative/rejection response. If set, the requesting domain must wait
      this many days before submitting another request of the same type. Helps prevent request spam and gives
      administrators time to review persistent requesters. Null or 0 means no waiting period.""")
  @Column(name = "wait_days_apply")
  private String waitDaysAplly;

  public GTNetMessageCodeType getRequestMsgCode() {
    return GTNetMessageCodeType.getGTNetMessageCodeTypeByValue(requestMsgCode);
  }

  public void setRequestMsgCode(GTNetMessageCodeType requestMsgCode) {
    this.requestMsgCode = requestMsgCode.getValue();
  }

  public GTNetMessageCodeType getResponseMsgCode1() {
    return GTNetMessageCodeType.getGTNetMessageCodeTypeByValue(responseMsgCode1);
  }

  public void setResponseMsgCode1(GTNetMessageCodeType responseMsgCode1) {
    this.responseMsgCode1 = responseMsgCode1.getValue();
  }

  public String getResponseMsgConditional1() {
    return responseMsgConditional1;
  }

  public void setResponseMsgConditional1(String responseMsgConditional1) {
    this.responseMsgConditional1 = responseMsgConditional1;
  }

  public String getResponseMsgMessage1() {
    return responseMsgMessage1;
  }

  public void setResponseMsgMessage1(String responseMsgMessage1) {
    this.responseMsgMessage1 = responseMsgMessage1;
  }

  public GTNetMessageCodeType getResponseMsgCode2() {
    return GTNetMessageCodeType.getGTNetMessageCodeTypeByValue(responseMsgCode2);
  }

  public void setResponseMsgCode2(GTNetMessageCodeType responseMsgCode2) {
    this.responseMsgCode2 = responseMsgCode2.getValue();
  }

  public String getResponseMsgConditional2() {
    return responseMsgConditional2;
  }

  public void setResponseMsgConditional2(String responseMsgConditional2) {
    this.responseMsgConditional2 = responseMsgConditional2;
  }

  public String getResponseMsgMessage2() {
    return responseMsgMessage2;
  }

  public void setResponseMsgMessage2(String responseMsgMessage2) {
    this.responseMsgMessage2 = responseMsgMessage2;
  }

  public GTNetMessageCodeType getResponseMsgCode3() {
    return GTNetMessageCodeType.getGTNetMessageCodeTypeByValue(responseMsgCode3);
  }

  public void setResponseMsgCode3(GTNetMessageCodeType responseMsgCode3) {
    this.responseMsgCode3 = responseMsgCode3.getValue();
  }

  public String getResponseMsgConditional3() {
    return responseMsgConditional3;
  }

  public void setResponseMsgConditional3(String responseMsgConditional3) {
    this.responseMsgConditional3 = responseMsgConditional3;
  }

  public String getResponseMsgMessage3() {
    return responseMsgMessage3;
  }

  public void setResponseMsgMessage3(String responseMsgMessage3) {
    this.responseMsgMessage3 = responseMsgMessage3;
  }

  public String getWaitDaysAplly() {
    return waitDaysAplly;
  }

  public void setWaitDaysAplly(String waitDaysAplly) {
    this.waitDaysAplly = waitDaysAplly;
  }

}
