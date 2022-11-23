package grafioschtrader.gtnet.m2m.model;

import java.util.Date;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.entities.GTNetMessage.GTNetMessageParam;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The model for the first contact with a remote server.")
public class MessageEnvelope {
 
  @Schema(description = "Source machine")
  public String sourceDomain;
  public Integer souceIdForReply;
  public Date timestamp;
  public byte messageCode;
  public Map<String, GTNetMessageParam> gtNetMessageParamMap;
  public String message;
  public JsonNode payload;
  
  public MessageEnvelope() {
  }

  public MessageEnvelope(String sourceDomain, GTNetMessage gtNetMsg) {
    this.sourceDomain = sourceDomain;
    this.souceIdForReply = gtNetMsg.getIdGtNetMessage();
    this.timestamp = gtNetMsg.getTimestamp();
    this.messageCode = gtNetMsg.getMessageCode().getValue();
    this.gtNetMessageParamMap = gtNetMsg.getGtNetMessageParamMap();
    this.message = gtNetMsg.getMessage();
  }
}
