package grafioschtrader.entities;

import grafioschtrader.gtnet.GTNetMessageCodeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = GTNetMessageAnswer.TABNAME)
@Schema(description = """
Certain incoming messages require a response. This can be an affirmative, a negative or a manual response. 
The automatic reply can be made according to specified conditions. The corresponding answer is determined by conditions, 
using the EvalEx framework.""")

public class GTNetMessageAnswer {
  public static final String TABNAME = "gt_net_message_answer";
  
  @Id
  @Column(name = "request_msg_code")
  private byte requestMsgCode;
  
  @Column(name = "response_msg_code1")
  private byte responseMsgCode1;
  
  @Column(name = "response_msg_conditional1")
  private String responseMsgConditional1;
  
  @Column(name = "response_msg_message1")
  private String responseMsgMessage1;
  
  @Column(name = "response_msg_code2")
  private Byte responseMsgCode2;
  
  @Schema(description = "2nd: This response is given if there is no condition or if this condition is met.")
  @Column(name = "response_msg_conditional2")
  private String responseMsgConditional2;
  
  @Schema(description = "2nd: Contains optional a message. This message is created by the user")
  @Column(name = "response_msg_message2")
  private String responseMsgMessage2;
  
  @Column(name = "response_msg_code3")
  private Byte responseMsgCode3;
  
  @Schema(description = "3nd: This response is given if there is no condition or if this condition is met.")
  @Column(name = "response_msg_conditional3")
  private String responseMsgConditional3;
  
  @Schema(description = "Thrid: Contains optional a message. This message is created by the user")
  @Column(name = "response_msg_message3")
  private String responseMsgMessage3;
 
  @Schema(description = "If the answer was negative, it is necessary to wait minimally so many days for the next apply.")
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
    this.responseMsgCode1 = responseMsgCode1.getValue();;
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
    this.responseMsgCode2 = responseMsgCode2.getValue();;
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
    this.responseMsgCode3 = responseMsgCode3.getValue();;
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
  