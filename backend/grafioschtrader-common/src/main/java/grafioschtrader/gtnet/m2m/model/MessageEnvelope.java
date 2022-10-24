package grafioschtrader.gtnet.m2m.model;

import java.util.Date;

import com.fasterxml.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The model for the first contact with a remote server.")
public class MessageEnvelope {
 
  public String domainRemoteName;
  public Date timestamp;
  public Short messageCode;
  public String timeZone;
  public JsonNode msgDetails;
  public String message;
  
  public MessageEnvelope() {
  }
  
  public MessageEnvelope(String domainRemoteName, Date timestamp, Short messageCode, String timeZone,
      JsonNode msgDetails, String message) {
    super();
    this.domainRemoteName = domainRemoteName;
    this.timestamp = timestamp;
    this.messageCode = messageCode;
    this.timeZone = timeZone;
    this.msgDetails = msgDetails;
    this.message = message;
  } 
  
  
}
