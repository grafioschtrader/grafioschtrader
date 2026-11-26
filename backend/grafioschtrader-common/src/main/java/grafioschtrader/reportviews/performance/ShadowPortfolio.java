package grafioschtrader.reportviews.performance;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.common.MoneyWeightedReturn;
import grafioschtrader.common.MoneyWeightedReturn.DatedFlow;
import grafioschtrader.common.ReturnSeries.Convention;
import grafioschtrader.common.ReturnSeries.FlowTiming;
import grafioschtrader.common.ReturnSeries.ValuationPoint;

//@formatter:off
/**
 * The shadow portfolio: every external flow of a portfolio invested in its benchmark instead, with fractional units,
 * no trading costs and the flow timing of a {@link Convention}. A pure function of its input, fixed by hand-calculated
 * tests, shared by the period performance report, the dashboard and the historical replay.
 *
 * <pre>
 * S_k = V_k
 * inflow at start, outflow at end:  S_i = (S_(i-1) + in_i) * b_i / b_(i-1) - out_i
 * all flows at start:               S_i = (S_(i-1) + in_i - out_i) * b_i / b_(i-1)
 * </pre>
 *
 * <p>
 * A withdrawal the shadow portfolio cannot fund ends it: with the outflow at the end when the value after the
 * withdrawal is negative, with all flows at the start when the capital before the benchmark move is negative. The
 * curve stops at the last funded valuation and the terminal figures stay empty.
 * </p>
 */
//@formatter:on
public final class ShadowPortfolio {

  private ShadowPortfolio() {
  }

  /**
   * Values of the shadow portfolio and its summary.
   *
   * @param values  shadow value per valuation point, null outside the span and after a shortfall
   * @param summary the figures of the comparison span, null without a span
   */
  public record Result(Double[] values, ShadowPortfolioResult summary) {
  }

  /**
   * Calculates the shadow portfolio over the span {@code [k, e]}.
   *
   * @param points     valuation points of the portfolio with their flows
   * @param levels     benchmark levels in main currency on the same dates, positive inside the span
   * @param k          first index of the span, -1 without a span
   * @param e          last index of the span
   * @param convention flow timing of the series the comparison belongs to
   * @param flows      dated external flows for the money-weighted return, ascending by date
   * @return the values and the summary
   */
  public static Result calculate(List<ValuationPoint> points, Double[] levels, int k, int e, Convention convention,
      List<DatedFlow> flows) {
    Double[] values = new Double[points.size()];
    if (k < 0 || e < k) {
      return new Result(values, null);
    }
    boolean outflowAtEnd = convention.timing() == FlowTiming.INFLOW_START_OUTFLOW_END;
    double shadow = points.get(k).value();
    values[k] = shadow;
    double netFlow = 0;
    for (int i = k + 1; i <= e; i++) {
      ValuationPoint point = points.get(i);
      double ratio = levels[i] / levels[i - 1];
      double next;
      if (outflowAtEnd) {
        next = (shadow + point.inflow()) * ratio - point.outflow();
      } else {
        double base = shadow + point.inflow() - point.outflow();
        next = base < 0 ? base : base * ratio;
      }
      if (next < 0) {
        return new Result(values, new ShadowPortfolioResult(money(values[k]), null, null, null, null, null, null,
            point.date()));
      }
      shadow = next;
      netFlow += point.inflow() - point.outflow();
      values[i] = shadow;
    }
    return new Result(values, summary(points, values, k, e, netFlow, flows));
  }

  private static ShadowPortfolioResult summary(List<ValuationPoint> points, Double[] values, int k, int e,
      double netFlow, List<DatedFlow> flows) {
    LocalDate start = points.get(k).date();
    LocalDate end = points.get(e).date();
    double startValue = values[k];
    double endValue = values[e];
    Double mwr = null;
    Double mwrAnnualized = null;
    MoneyWeightedReturn.Result solved = null;
    if (endValue > 0) {
      List<DatedFlow> span = flows.stream().filter(f -> f.date().isAfter(start) && !f.date().isAfter(end)).toList();
      solved = MoneyWeightedReturn.solve(start, startValue, end, endValue, span);
      mwr = PerformancePeriodMetrics.percent(MoneyWeightedReturn.periodReturn(solved));
      mwrAnnualized = PerformancePeriodMetrics
          .percent(MoneyWeightedReturn.annualizedReturn(solved, ChronoUnit.DAYS.between(start, end)));
    }
    return new ShadowPortfolioResult(money(startValue), money(endValue), money(endValue - startValue - netFlow),
        money(points.get(e).value() - endValue), mwr, mwrAnnualized, solved == null ? null : solved.status(), null);
  }

  private static Double money(Double amount) {
    return amount == null || !Double.isFinite(amount) ? null : DataBusinessHelper.roundStandard(amount);
  }
}
