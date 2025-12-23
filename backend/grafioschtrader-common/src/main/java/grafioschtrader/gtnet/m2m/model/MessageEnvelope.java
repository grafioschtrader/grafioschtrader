package grafioschtrader.gtnet.m2m.model;

import java.util.Date;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.entities.GTNetMessage.GTNetMessageParam;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Wrapper for all machine-to-machine (M2M) communication between GTNet instances.
 *
 * Every HTTP request/response between peers is wrapped in a MessageEnvelope, providing:
 * <ul>
 *   <li>Sender identification (sourceDomain)</li>
 *   <li>Reply correlation (souceIdForReply links to the sender's message ID)</li>
 *   <li>Message type and parameters (messageCode, gtNetMessageParamMap)</li>
 *   <li>Optional arbitrary payload (JSON node for type-specific data like GTNet during handshake)</li>
 * </ul>
 *
 */
@Schema(description = """
    Wrapper for machine-to-machine (M2M) communication between GTNet instances. Every HTTP request/response is
    wrapped in this envelope, providing sender identification, reply correlation, message type with parameters,
    and optional JSON payload for type-specific data. Used by BaseDataClient for sending and GTNetM2MResource
    for receiving.""")
public class MessageEnvelope {

  @Schema(description = """
      Base URL of the sending domain (e.g., 'https://example.com:8080'). Identifies the origin of the message
      and is used by the receiver to look up the corresponding GTNet entry for token validation.""")
  public String sourceDomain;

  @Schema(description = """
      The sender's local message ID (idGtNetMessage). The receiver stores this in their idSourceGtNetMessage field
      when saving the received message, enabling cross-system message correlation.""")
  public Integer idSourceGtNetMessage;

  @Schema(description = "UTC timestamp when the message was created. Used for ordering and staleness detection.")
  public Date timestamp;

  @Schema(description = "Message type code from GTNetMessageCodeType. Determines how the receiver processes this message.")
  public byte messageCode;

  @Schema(description = """
      Typed parameters specific to this message code. Key is parameter name, value contains the actual data.
      Structure defined by GTNetModelHelper for each message type.""")
  public Map<String, GTNetMessageParam> gtNetMessageParamMap;

  @Schema(description = """
      Indicates whether the sending server is currently busy. When true, the recipient should limit communication to
      server status changes only. This flag is set from the source GTNet entry's serverBusy field and allows
      capacity-constrained servers to reduce message load.""")
  public boolean serverBusy;
  
  @Schema(description = "Optional free-text message for human-readable context or notes.")
  public String message;

  @Schema(description = """
      Optional JSON payload for complex, type-specific data. Used during handshake to transmit the full GTNet
      entity of the sender. Can contain any serializable object based on the message code.""")
  public JsonNode payload;

  @Schema(description = """
      The sender's GTNet entry containing current status and capabilities. Always included in every message
      to keep the receiver's local copy of this remote's configuration synchronized. Uses GTNetPublicDTO
      to exclude sensitive authentication tokens.""")
  public GTNetPublicDTO sourceGtNet;

  public MessageEnvelope() {
  }

  public MessageEnvelope(GTNet sourceGtNet, GTNetMessage gtNetMsg) {
    this(sourceGtNet, gtNetMsg, sourceGtNet.isServerBusy());
  }

  /**
   * Creates a new MessageEnvelope with the source server's GTNet entry and busy status.
   *
   * @param sourceGtNet the GTNet entry of the sending server (converted to DTO to exclude tokens)
   * @param gtNetMsg the message to wrap
   * @param serverBusy whether the source server is currently busy
   */
  public MessageEnvelope(GTNet sourceGtNet, GTNetMessage gtNetMsg, boolean serverBusy) {
    this.sourceDomain = sourceGtNet.getDomainRemoteName();
    this.idSourceGtNetMessage = gtNetMsg.getIdGtNetMessage();
    this.timestamp = gtNetMsg.getTimestamp();
    this.messageCode = gtNetMsg.getMessageCode().getValue();
    this.gtNetMessageParamMap = gtNetMsg.getGtNetMessageParamMap();
    this.message = gtNetMsg.getMessage();
    this.serverBusy = serverBusy;
    this.sourceGtNet = new GTNetPublicDTO(sourceGtNet);
  }
}
