package grafioschtrader.gtnet.m2m.model;

import com.fasterxml.jackson.databind.JsonNode;

public class MessageResponse {
  public MessageEnvelope messageEnvelope;
  public JsonNode details;

  public MessageResponse(MessageEnvelope messageEnvelope, JsonNode details) {
    this.messageEnvelope = messageEnvelope;
    this.details = details;
  }
    
}
