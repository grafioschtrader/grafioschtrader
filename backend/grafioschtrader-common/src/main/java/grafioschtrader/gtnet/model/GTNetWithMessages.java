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
 *   <li>Identification of which entry represents the local instance</li>
 * </ul>
 *
 * The message map is keyed by idGtNet, allowing the UI to efficiently look up messages for each domain
 * when expanding rows in the tree table.
 */
@Schema(description = """
    Combined response for the GTNet setup screen. Contains all known domains, their message history grouped by
    domain ID, and the local instance identifier. Designed for efficient rendering in the GTNetSetupTableComponent
    tree table view.""")
public class GTNetWithMessages {

  @Schema(description = "List of all GTNet domain configurations known to this instance.")
  public List<GTNet> gtNetList;

  @Schema(description = """
      Message history grouped by domain ID. Key is idGtNet, value is the list of messages (sent and received)
      for that domain, ordered by timestamp. Used for tree table expansion in the UI.""")
  public Map<Integer, List<GTNetMessage>> gtNetMessageMap;

  @Schema(description = """
      The idGtNet of the entry representing this local instance. Null if this instance hasn't been registered
      in GTNet yet. Used by the UI to highlight the 'self' row and enable/disable certain actions.""")
  public Integer gtNetMyEntryId;

  public GTNetWithMessages(List<GTNet> gtNetList, Map<Integer, List<GTNetMessage>> gtNetMessageMap,
      Integer gtNetMyEntryId) {
    this.gtNetList = gtNetList;
    this.gtNetMessageMap = gtNetMessageMap;
    this.gtNetMyEntryId = gtNetMyEntryId;
  }

}
