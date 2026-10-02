package grafioschtrader.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    The alerts configured in one AlgoTop hierarchy, grouped by the node they hang on. Only the hierarchy referenced by
    the main tenant's monitoring assignment is evaluated live; the alerts of every other hierarchy are kept but stay
    dormant, and a historical replay never evaluates alerts at all. This overview lets the user see which alerts can
    actually report something.""")
public record AlgoTopAlertGroupDto(@Schema(description = "ID of the AlgoTop node") Integer idAlgoTop,
    @Schema(description = "Name of the hierarchy") String name,
    @Schema(description = """
        Whether this hierarchy is the main tenant's assigned monitoring hierarchy. When false, none of its alerts is
        evaluated live.""") boolean assigned,
    @Schema(description = "Nodes carrying at least one alert, in tree order: top, then each bucket followed by its instruments") List<NodeAlerts> nodes) {

  /** Level of the hierarchy node an alert hangs on; it decides which instruments the alert is applied to. */
  public enum NodeLevel {
    /** Applies to every instrument of the watchlist linked to the AlgoTop. */
    TOP,
    /** Applies to the instruments of the bucket. */
    ASSETCLASS,
    /** Applies to the instrument of the node. */
    SECURITY
  }

  @Schema(description = "One hierarchy node together with the alerts configured on it")
  public record NodeAlerts(@Schema(description = "ID of the hierarchy node") Integer idNode,
      @Schema(description = "Readable name of the node in the language of the user") String nodeName,
      @Schema(description = "Level of the node in the hierarchy") NodeLevel nodeLevel,
      @Schema(description = "Alert strategies configured on this node") List<StrategyAlert> alerts) {
  }

  @Schema(description = "One alert strategy and whether the live evaluation considers it")
  public record StrategyAlert(@Schema(description = "ID of the alert strategy") Integer idAlgoRuleStrategy,
      @Schema(description = "Implementation type of the alert, its enum name") String algoStrategyImplementations,
      @Schema(description = "The user's live alert switch of this strategy") boolean alertEnabled,
      @Schema(description = """
          Whether the live evaluation considers this alert: its hierarchy is the assigned one, the strategy is
          activatable and its alert switch is on.""") boolean effectiveActive) {
  }
}
