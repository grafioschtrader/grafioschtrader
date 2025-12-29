package grafioschtrader.gtnet.model;

import java.util.Map;

import grafioschtrader.entities.GTNetMessage.GTNetMessageParam;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request DTO for submitting GTNet messages from the UI.
 *
 * This DTO captures the user's intent when creating a new message via GTNetMessageEditComponent.
 * The frontend builds this object based on:
 * <ul>
 *   <li>Selected target domain (from GTNetSetupTableComponent row)</li>
 *   <li>Selected message type (from dropdown populated by msgformdefinition)</li>
 *   <li>Dynamic form fields (generated from GTNetModelHelper descriptors)</li>
 *   <li>Optional reply context (when responding to a received message)</li>
 * </ul>
 *
 */
@Schema(description = """
    Request DTO for submitting GTNet messages from the UI. Captures target domain, message type, typed parameters,
    and optional reply context. For broadcast messages (codes containing '_ALL_'), idGTNetTargetDomain is ignored
    and the message is sent to all applicable domains.""")
public class MsgRequest {

  @Schema(description = """
      ID of the target GTNet domain for point-to-point messages. Ignored for broadcast messages (codes containing
      '_ALL_'), which are sent to all domains matching specific criteria (e.g., all with acceptLastpriceRequest).""")
  public Integer idGTNetTargetDomain;

  @Schema(description = """
      Local message ID this new message is replying to. Establishes conversation threading. Null for initial
      messages that don't respond to anything.""")
  public Integer replyTo;

  @Schema(description = """
      The type of message to send. Determines the expected gtNetMessageParamMap structure and whether a
      synchronous response is expected. Must be a client-initiated code (ending with '_C').""")
  public GTNetMessageCodeType messageCode;

  @Schema(description = """
      Typed parameters specific to the selected messageCode. Structure must match the model class registered
      in GTNetModelHelper for this code. Keys are field names, values contain the actual data.""")
  public Map<String, GTNetMessageParam> gtNetMessageParamMap;

  @Schema(description = """
      Optional free-text note to include with the message. Displayed alongside structured parameters in the
      recipient's UI. Useful for human context like 'Please respond by Friday' or 'Test message, please ignore'.""")
  public String message;

  @Schema(description = """
      Cooling-off period in days after a negative/rejection response. Only applicable for response messages
      (ACCEPT/REJECT codes). If set, the requesting domain must wait this many days before submitting another
      request of the same type. 0 means no waiting period.""")
  public Short waitDaysApply;
}
