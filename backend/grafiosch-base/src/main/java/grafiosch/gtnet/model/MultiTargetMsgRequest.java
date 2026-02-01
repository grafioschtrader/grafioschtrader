package grafiosch.gtnet.model;

import java.util.List;
import java.util.Map;

import grafiosch.entities.GTNetMessage.GTNetMessageParam;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request DTO for submitting GTNet admin messages to multiple targets via multi-select.
 *
 * <p>
 * Unlike {@link MsgRequest} which targets a single domain, this DTO allows selecting multiple
 * target domains for a single admin message. The message is created once and delivered to all
 * targets via background processing using {@code GTNetMessageAttempt} entries.
 * </p>
 *
 * <p>
 * This approach is used for admin messages (GT_NET_ADMIN_MESSAGE_SEL_C) when the administrator
 * selects multiple peers using checkboxes in the GTNetAdminMessagesComponent.
 * </p>
 */
@Schema(description = """
    Request DTO for submitting GTNet admin messages to multiple targets. Allows administrators to
    send a single message to multiple selected peers using background delivery via GTNetMessageAttempt.
    Only supports GT_NET_ADMIN_MESSAGE_SEL_C message code.""")
public class MultiTargetMsgRequest {

  @Schema(description = """
      List of GTNet domain IDs that should receive this message. Must contain at least one ID.
      These are the targets selected via checkboxes in the admin messages component.""")
  public List<Integer> idGTNetTargetDomains;

  @Schema(description = """
      Optional free-text note to include with the message. Displayed in the recipient's UI.""")
  public String message;

  @Schema(description = """
      Typed parameters for the message. For admin messages this is typically empty or contains
      custom fields as defined by the message model.""")
  public Map<String, GTNetMessageParam> gtNetMessageParamMap;

  @Schema(description = """
      Visibility level for admin messages. Controls who can see the message:
      ALL_USERS = visible to everyone, ADMIN_ONLY = visible only to administrators.""")
  public String visibility;
}
