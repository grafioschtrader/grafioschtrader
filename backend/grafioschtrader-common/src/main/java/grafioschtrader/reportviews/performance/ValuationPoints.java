package grafioschtrader.reportviews.performance;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

import grafioschtrader.common.ReturnSeries.ValuationPoint;

/**
 * Turns daily holdings into the valuation points of a time-weighted series. Shared by the period performance report and
 * the dashboard, so both value a day and split its flows the same way and therefore agree on every day both show.
 */
public final class ValuationPoints {

  private ValuationPoints() {
  }

  /**
   * Builds the points. The value includes the open result of margin positions. The net flow of an interval comes from
   * the cumulative external cash transfer of the holdings; the dated flows only tell how much of it was withdrawn, so
   * that the withdrawals count as flowed at the end and the rest as flowed at the start of the interval.
   *
   * @param holdings   daily holdings in ascending order of the day, the first one is the excluded base
   * @param dailyFlows net external flows per calendar day, ascending by date
   * @param regular    decides from the previous and the current day whether an interval misses no expected trading day
   * @return one point per holding
   */
  public static List<ValuationPoint> of(List<? extends IPeriodHolding> holdings, List<IDailyExternalFlow> dailyFlows,
      BiPredicate<LocalDate, LocalDate> regular) {
    List<ValuationPoint> points = new ArrayList<>(holdings.size());
    int flowIndex = 0;
    for (int i = 0; i < holdings.size(); i++) {
      IPeriodHolding holding = holdings.get(i);
      LocalDate date = holding.getDate();
      if (i == 0) {
        points.add(new ValuationPoint(date, totalValue(holding), 0, 0, true));
        continue;
      }
      LocalDate dateBefore = holdings.get(i - 1).getDate();
      double outflow = 0;
      for (; flowIndex < dailyFlows.size() && !dailyFlows.get(flowIndex).getFlowDate().isAfter(date); flowIndex++) {
        IDailyExternalFlow flow = dailyFlows.get(flowIndex);
        if (flow.getFlowDate().isAfter(dateBefore) && flow.getFlowMC() < 0) {
          outflow -= flow.getFlowMC();
        }
      }
      double netFlow = holding.getExternalCashTransferMC() - holdings.get(i - 1).getExternalCashTransferMC();
      points.add(new ValuationPoint(date, totalValue(holding), Math.max(0, netFlow + outflow), outflow,
          regular.test(dateBefore, date)));
    }
    return points;
  }

  /** Cash, securities and the open result of margin positions. */
  public static double totalValue(IPeriodHolding holding) {
    return holding.getCashBalanceMC() + holding.getSecuritiesMC() + holding.getMarginCloseGainMC();
  }
}
