package grafioschtrader.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    Whether a rule-based strategy can be used as it stands. It is derived from the hierarchy every time it is read and is
    never stored, because a weighting edited on any level, an instrument whose active period ends or a watchlist that
    loses a security change it without the strategy itself being saved. A replay and the comparison report refuse a
    strategy that is not ready instead of failing half way through.""")
public record AlgoTopReadiness(
    @Schema(description = """
        True when a simulation environment can be created from the strategy and a replay can be started. A strategy
        without a portfolio rebalance is ready for this, because only the strategies of its securities trade then.""") boolean readyForReplay,
    @Schema(description = """
        True when the rebalancing comparison report and the daily rebalancing evaluation can be computed. Requires
        readyForReplay and a portfolio rebalance on the top level.""") boolean readyForRebalancing,
    @Schema(description = "All findings, those that block first") List<Issue> issues) {

  @Schema(description = "One finding of the readiness check")
  public record Issue(
      @Schema(description = "Message key of the finding, also used as the message key when a gate refuses the strategy") String code,
      @Schema(description = "Hierarchy node the finding belongs to, null for a finding about the strategy as a whole") Integer idNode,
      @Schema(description = """
          Property path of the node the tree highlights for this finding, or null when the finding has no column of its
          own""") String field,
      @Schema(description = "Message arguments: the label of the node, followed by a detail such as the actual sum") Object[] args,
      @Schema(description = "The finding translated into the language of the user") String message,
      @Schema(description = """
          True when the finding makes a replay or the rebalancing fail. A finding that is not blocking only marks
          something that is skipped, for example an asset class without a tradable security.""") boolean blocking) {
  }
}
