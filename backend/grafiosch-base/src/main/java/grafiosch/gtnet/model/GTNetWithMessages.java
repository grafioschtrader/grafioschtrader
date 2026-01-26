package grafiosch.gtnet.model;

import java.util.List;
import java.util.Map;

import grafiosch.entities.GTNet;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Combined response DTO for the GTNet setup screen, providing all domains and message counts.
 *
 * This DTO is designed to support the GTNetSetupTableComponent frontend view, which displays:
 * <ul>
 *   <li>A list of all known GTNet domains with their configuration</li>
 *   <li>Message counts per domain (for determining if expander should show)</li>
 *   <li>Pending reply counts for unanswered requests (columns: "To be answered", "Answer expected")</li>
 *   <li>Identification of which entry represents the local instance</li>
 * </ul>
 *
 * Messages are loaded lazily when a row is expanded via separate REST call.
 * The pending reply maps are keyed by idGtNet for efficient UI rendering.
 */
@Schema(description = """
    Combined response for the GTNet setup screen. Contains all known domains, message counts per domain,
    pending reply maps, and the local instance identifier. Messages are loaded lazily when expanded.
    Designed for efficient rendering in the GTNetSetupTableComponent tree table view.""")
public class GTNetWithMessages {

  @Schema(description = "List of all GTNet domain configurations known to this instance.")
  public List<GTNet> gtNetList;

  @Schema(description = """
      Message count per domain ID. Key is idGtNet, value is the count of messages for that domain.
      Used to determine if the expander icon should be shown in the UI.""")
  public Map<Integer, Integer> gtNetMessageCountMap;

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

  @Schema(description = """
      ID of an open GT_NET_OPERATION_DISCONTINUED_ALL_C message if one exists. Only one such message can be open
      at a time per instance. An 'open' message is one that has been sent with a closeStartDate in the future
      and has not been cancelled. Null if no open discontinued message exists. Used by the UI to disable
      creating a new discontinued message when one is already open.""")
  public Integer idOpenDiscontinuedMessage;

  public GTNetWithMessages(List<GTNet> gtNetList, Map<Integer, Integer> gtNetMessageCountMap,
      Map<Integer, List<Integer>> outgoingPendingReplies, Map<Integer, List<Integer>> incomingPendingReplies,
      Integer gtNetMyEntryId, Integer idOpenDiscontinuedMessage) {
    this.gtNetList = gtNetList;
    this.gtNetMessageCountMap = gtNetMessageCountMap;
    this.outgoingPendingReplies = outgoingPendingReplies;
    this.incomingPendingReplies = incomingPendingReplies;
    this.gtNetMyEntryId = gtNetMyEntryId;
    this.idOpenDiscontinuedMessage = idOpenDiscontinuedMessage;
  }
}
