package grafioschtrader.reportviews.performance;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.common.MoneyWeightedReturn;
import grafioschtrader.common.ReturnSeries;
import grafioschtrader.types.MoneyWeightedReturnStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    Relative return, risk and cost figures of the period performance report, calculated on every request from the daily
    series of the report, the dated external flows and the fee bookings of the period. Percentages are expressed in
    percent (12.34 means 12.34 %). A figure that cannot be determined is null, never NaN or Infinity.""")
public record PerformancePeriodMetrics(
    @Schema(description = "Calendar days from the excluded base date to the last date of the period") int calendarDays,
    @Schema(description = "Weekdays in the period that are no holiday of an exchange of the held instruments") int expectedSessions,
    @Schema(description = "Days of the period with complete prices, i.e. the number of valuations after the base") int valuedSessions,
    @Schema(description = "Number of returns spanning at least one expected trading day without complete prices") int gapIntervals,
    @Schema(description = "Number of daily returns entering the volatility") int returnObservations,
    @Schema(description = "Time-weighted return of the period in percent; null without a usable daily return") Double twrPercent,
    @Schema(description = "Time-weighted return p.a. in percent; null for periods shorter than 360 calendar days") Double twrAnnualizedPercent,
    @Schema(description = "Money-weighted return (internal rate of return) in percent; null unless mwrStatus is MWR_CALCULATED or MWR_TOTAL_LOSS") Double mwrPercent,
    @Schema(description = "Money-weighted return p.a. in percent; null for periods shorter than 360 calendar days") Double mwrAnnualizedPercent,
    @Schema(description = "Outcome of the internal rate of return search, explains an empty money-weighted return") MoneyWeightedReturnStatus mwrStatus,
    @Schema(description = "Largest decline of the time-weighted value from a previous high in percent (at most 0); null without a usable return") Double maxDrawdownPercent,
    @Schema(description = "Date of the high before the largest decline; null without decline") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate drawdownPeakDate,
    @Schema(description = "Date of the low of the largest decline; null without decline") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate drawdownTroughDate,
    @Schema(description = "Date the previous high was regained; null when not regained by the end of the period") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate drawdownRecoveryDate,
    @Schema(description = "Distance of the last day from the highest time-weighted value of the period in percent") Double currentDrawdownPercent,
    @Schema(description = "Annualized standard deviation of the regular daily returns in percent; null below 20 returns") Double volatilityAnnualizedPercent,
    @Schema(description = "Best complete period step (day or month) in percent; null without complete step") Double bestStepPercent,
    @Schema(description = "Last date of the best complete period step") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate bestStepDate,
    @Schema(description = "Worst complete period step (day or month) in percent; null without complete step") Double worstStepPercent,
    @Schema(description = "Last date of the worst complete period step") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate worstStepDate,
    @Schema(description = "Capital at the start of each interval, weighted with its calendar days, in main currency") Double averageCapitalMC,
    @Schema(description = "Separately booked account and custody fees of the period in main currency; a charge is positive") double feesMC,
    @Schema(description = "Fees in percent of the average invested capital; null when that capital is not positive or a fee lacks its exchange rate") Double feeRatioPercent,
    @Schema(description = "Fee ratio scaled linearly to 365 days; null for periods shorter than 360 calendar days or when the fee ratio is null") Double feeRatioAnnualizedPercent,
    @Schema(description = "Fee bookings excluded because their exchange rate is unavailable") int feesWithoutRate) {

  /** Fewest regular daily returns from which the volatility is shown. */
  public static final int MIN_VOLATILITY_OBSERVATIONS = 20;

  /**
   * Builds the figures from the calculated series and converts every ratio into a rounded percentage.
   *
   * @param series           time-weighted series of the report, including the excluded base as first point
   * @param mwr              result of the internal rate of return search
   * @param expectedSessions expected trading days in the period
   * @param steps            all period steps of the report; only complete ones with a return are ranked
   * @param feesMC           fees booked in the period, a charge is positive
   * @return the figures of the period
   */
  public static PerformancePeriodMetrics of(ReturnSeries series, MoneyWeightedReturn.Result mwr, int expectedSessions,
      List<PeriodStep> steps, double feesMC) {
    return of(series, mwr, expectedSessions, steps, feesMC, 0);
  }

  /** Builds the figures including the count of fee bookings without a conversion rate. */
  public static PerformancePeriodMetrics of(ReturnSeries series, MoneyWeightedReturn.Result mwr, int expectedSessions,
      List<PeriodStep> steps, double feesMC, int skippedFees) {
    long days = series.calendarDays();
    Double twr = series.totalReturn();
    ReturnSeries.Drawdown drawdown = series.drawdown();
    List<PeriodStep> ranked = steps.stream().filter(s -> s.complete && s.twrPercent != null).toList();
    Optional<PeriodStep> best = ranked.stream().max(Comparator.comparingDouble(s -> s.twrPercent));
    Optional<PeriodStep> worst = ranked.stream().min(Comparator.comparingDouble(s -> s.twrPercent));
    Double averageCapital = series.averageCapital();
    // A ratio of the known fees only would understate the cost, so an unconvertible fee leaves it undetermined.
    Double feeRatio = averageCapital == null || averageCapital <= 0 || skippedFees > 0 ? null
        : feesMC / averageCapital;
    Double feeRatioAnnualized = feeRatio == null || days < ReturnSeries.MIN_ANNUALIZATION_DAYS ? null
        : feeRatio * 365.0 / days;
    return new PerformancePeriodMetrics((int) days, expectedSessions, Math.max(0, series.size() - 1),
        series.gapIntervals(), series.volatilityObservations(), percent(twr),
        percent(ReturnSeries.annualize(twr, days, ReturnSeries.MIN_ANNUALIZATION_DAYS)),
        percent(MoneyWeightedReturn.periodReturn(mwr)), percent(MoneyWeightedReturn.annualizedReturn(mwr, days)),
        mwr.status(), percent(drawdown.depth()), drawdown.peakDate(), drawdown.troughDate(), drawdown.recoveryDate(),
        percent(drawdown.current()), percent(series.annualizedVolatility(MIN_VOLATILITY_OBSERVATIONS)),
        best.map(s -> s.twrPercent).orElse(null), best.map(s -> s.lastDate).orElse(null),
        worst.map(s -> s.twrPercent).orElse(null), worst.map(s -> s.lastDate).orElse(null), money(averageCapital),
        DataBusinessHelper.roundStandard(feesMC), percent(feeRatio), percent(feeRatioAnnualized), skippedFees);
  }

  /** Ratio to rounded percentage; null and non-finite values stay empty. */
  public static Double percent(Double ratio) {
    return ratio == null || !Double.isFinite(ratio) ? null : DataBusinessHelper.roundPercentage(ratio * 100);
  }

  private static Double money(Double amount) {
    return amount == null || !Double.isFinite(amount) ? null : DataBusinessHelper.roundStandard(amount);
  }
}
