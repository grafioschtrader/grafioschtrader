package grafioschtrader.gtnet.model;

import java.util.List;
import java.util.Map;

import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetMessage;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Combined response DTO for the GTNet setup screen, providing all domains and their message history.
 *
 * This DTO is designed to support the GTNetSetupTableComponent frontend view, which displays:
 * <ul>
 *   <li>A list of all known GTNet domains with their configuration</li>
 *   <li>Message threads grouped by domain (for tree table expansion)</li>
 *   <li>Pending reply counts for unanswered requests (columns: "To be answered", "Answer expected")</li>
 *   <li>Identification of which entry represents the local instance</li>
 * </ul>
 *
 * The message map and pending reply maps are keyed by idGtNet for efficient UI rendering.
 */
@Schema(description = """
    Combined response for the GTNet setup screen. Contains all known domains, their message history grouped by
    domain ID, pending reply maps, and the local instance identifier. Designed for efficient rendering
    in the GTNetSetupTableComponent tree table view.""")
public class GTNetWithMessages {

  @Schema(description = "List of all GTNet domain configurations known to this instance.")
  public List<GTNet> gtNetList;

  @Schema(description = """
      Message history grouped by domain ID. Key is idGtNet, value is the list of messages (sent and received)
      for that domain, ordered by timestamp. Used for tree table expansion in the UI.""")
  public Map<Integer, List<GTNetMessage>> gtNetMessageMap;

  @Schema(description = """
      Outgoing pending replies grouped by domain ID. Key is idGtNet, value is the list of id_gt_net_message
      for outgoing requests (send_recv=0) that have not received a reply yet. Used for "Answer expected" column.""")
  public Map<Integer, List<Integer>> outgoingPendingReplies;

  @Schema(description = """
      Incoming pending replies grouped by domain ID. Key is idGtNet, value is the list of id_gt_net_message
      for incoming requests (send_recv=1) that have not been replied to yet. Used for "To be answered" column.""")
  public Map<Integer, List<Integer>> incomingPendingReplies;

  @Schema(description = """
      The idGtNet of the entry representing this local instance. Null if this instance hasn't been registered
      in GTNet yet. Used by the UI to highlight the 'self' row and enable/disable certain actions.""")
  public Integer gtNetMyEntryId;

  public GTNetWithMessages(List<GTNet> gtNetList, Map<Integer, List<GTNetMessage>> gtNetMessageMap,
      Map<Integer, List<Integer>> outgoingPendingReplies, Map<Integer, List<Integer>> incomingPendingReplies,
      Integer gtNetMyEntryId) {
    this.gtNetList = gtNetList;
    this.gtNetMessageMap = gtNetMessageMap;
    this.outgoingPendingReplies = outgoingPendingReplies;
    this.incomingPendingReplies = incomingPendingReplies;
    this.gtNetMyEntryId = gtNetMyEntryId;
  }
}
