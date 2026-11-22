package grafioschtrader.dto;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafioschtrader.types.SpinOffHistoryBasis;
import grafioschtrader.types.SpinOffHistoryBasisOrigin;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What the administrator needs to judge the provider split and the history basis of a spin-off.
 *
 * @param splits                     splits the split connector reports within 10 days of the action date
 * @param splitsUnavailable          true when the split connector could not be read
 * @param providerSplitFactor        factor of the split on the action date, which GT drops; null without one
 * @param spinOffFactor              provider factor divided by the stored splits on the action date
 * @param historySourceIsSplitSource true when history connector and URL equal split connector and URL
 * @param historyBasis               recorded history basis, null when no spin-off is recorded on the date
 * @param historyFactor              recorded history factor
 * @param historyBasisOrigin         how the recorded history basis was decided
 * @param historyConnector           history connector the recorded basis belongs to
 * @param historyUrlExtend           history URL extension the recorded basis belongs to
 * @param currentSource              true when the recorded basis belongs to the parent's current history source
 * @param storedStep                 price step of the stored series on the action date
 * @param deliveredStep              price step of the series the history connector delivers now
 */
@Schema(description = """
    What the administrator needs to judge the provider split and the history basis of a spin-off: the splits the split
    connector reports around the action date, the factor of the dropped split, the recorded history basis and the price
    step of the stored and of the delivered series on the action date.""")
public record SpinOffProviderSplitsDto(List<ProviderSplit> splits, boolean splitsUnavailable,
    Double providerSplitFactor, Double spinOffFactor, boolean historySourceIsSplitSource,
    SpinOffHistoryBasis historyBasis, Double historyFactor, SpinOffHistoryBasisOrigin historyBasisOrigin,
    String historyConnector, String historyUrlExtend, boolean currentSource, Step storedStep, Step deliveredStep) {

  /**
   * One split reported by the split connector.
   *
   * @param splitDate  split date
   * @param fromFactor units before the split
   * @param toFactor   units after the split
   * @param factor     toFactor / fromFactor
   * @param dropped    true when GT drops this split because a spin-off is recorded on its date
   */
  @Schema(description = "One split reported by the split connector; dropped when a spin-off falls on its date")
  public record ProviderSplit(@JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate splitDate,
      int fromFactor, int toFactor, double factor, boolean dropped) {
  }

  /**
   * Price step on the action date.
   *
   * @param x    ln(close on the action date / last close before)
   * @param mAdj market move of the action date if the series is adjusted for the spin-off, equal to x
   * @param mRaw market move if the series is unadjusted, x + ln provider factor; null without provider factor
   */
  @Schema(description = """
      Price step on the action date: x is the log return of the close, mAdj the market move if the series is adjusted
      for the spin-off, mRaw the move if it is unadjusted (x + ln provider factor).""")
  public record Step(double x, double mAdj, Double mRaw) {
  }
}
