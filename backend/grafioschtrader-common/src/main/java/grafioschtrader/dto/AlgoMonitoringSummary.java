package grafioschtrader.dto;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafioschtrader.types.AlgoRecommendationAction;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    Summary of the stored live plan of the hierarchy assigned to monitoring, as the daily evaluation left it. It answers
    whether the portfolio needs attention and when the next checkpoint falls due; the full comparison is the rebalancing
    report. When reasonKey is set, nothing else is filled.""")
public record AlgoMonitoringSummary(
    @Schema(description = "Why there is no summary: no hierarchy assigned, or not evaluated yet; null otherwise") String reasonKey,
    @Schema(description = """
        Translation key of the state: nothing to do, drift reported between two checkpoints, or checkpoint due with
        trades""") String statusKey,
    @Schema(description = "Id of the monitored AlgoTop, for opening its rebalancing report") Integer idAlgoTop,
    @Schema(description = "Name of the monitored AlgoTop") String algoTopName,
    @Schema(description = "Closing day the stored plan was calculated from") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate valuationDate,
    @Schema(description = "Tenant currency of every amount") String currency,
    @Schema(description = "The stored plan is a checkpoint that asks for trades") boolean periodicDue,
    @Schema(description = "Last periodic checkpoint; null when none has been recorded") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate lastCheckpointDate,
    @Schema(description = "First day the next checkpoint is due; null without a last checkpoint") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate nextCheckpointDate,
    @Schema(description = "Number of purchases the plan proposes, traded only on a checkpoint") int buyCount,
    @Schema(description = "Sum of the proposed purchases in tenant currency") double buyAmount,
    @Schema(description = "Number of proposed sales") int sellCount,
    @Schema(description = "Sum of the proposed sales in tenant currency") double sellAmount,
    @Schema(description = "Number of trades that would be needed but are blocked") int blockedCount,
    @Schema(description = "Label of the bucket furthest from its target; null without bucket lines") String largestDeviationBucket,
    @Schema(description = "Its actual minus target share, in percentage points of net equity") Double largestDeviationPercentage,
    @Schema(description = "Number of mean reversion proposals asking for a trade") int meanReversionSignals,
    @Schema(description = "The largest proposed trades, at most three, sales first") List<Trade> topTrades) {

  @Schema(description = "One proposed trade of the stored plan")
  public record Trade(@Schema(description = "Instrument name") String securityName,
      @Schema(description = "Direction of the proposed trade") AlgoRecommendationAction recommendedAction,
      @Schema(description = "Size of the trade in tenant currency") Double recommendedAmount,
      @Schema(description = "Units of the trade; null without a usable price") Double recommendedUnits) {
  }

  /** A summary that only carries the reason why there is nothing to summarize. */
  public static AlgoMonitoringSummary reason(String reasonKey) {
    return new AlgoMonitoringSummary(reasonKey, null, null, null, null, null, false, null, null, 0, 0, 0, 0, 0, null, null, 0,
        List.of());
  }
}
