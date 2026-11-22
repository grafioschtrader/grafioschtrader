package grafioschtrader.reportviews.performance;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

import grafioschtrader.common.ReturnSeries;
import grafioschtrader.common.ReturnSeries.Convention;
import grafioschtrader.common.ReturnSeries.ValuationPoint;
import grafioschtrader.types.SecurityPeriodRemark;

//@formatter:off
/**
 * Calculates the figures of one security for the period performance per security: absolute gain, time-weighted return
 * in main and security currency, holding days and the remark explaining missing figures.
 *
 * <p>
 * The values come from the holding periods valued with closing prices and closing rates, the flows from the
 * transaction replay in security currency. The class itself is a pure function of its input; it reads no repository
 * and holds no state, which is what lets every case be fixed with hand-calculated numbers.
 * </p>
 *
 * <ul>
 * <li>The <b>grid</b> of the security is the trading days of the period without the days on which it is held but not
 * valued. A trading day without a holding row is a grid day with value 0.</li>
 * <li>A flow of the period, dated by its business date after {@code dateFrom} and not after {@code dateTo}, is placed
 * on the first grid day on or after that date. Flows of the same placement day and the same security transfer are
 * netted, as are the parent sale and repurchase of one spin-off, which together move the market value of the child
 * out of the parent; every other flow is never netted.</li>
 * <li>A flow is converted into the main currency with the closing rate of its placement day, the rate the valuation of
 * that day uses.</li>
 * <li>{@code gain = value(dateTo) - value(dateFrom) - sum of the flows}, in both currencies.</li>
 * <li>The return uses {@link Convention#REPORT}: inflows count from the previous close, outflows at the close.</li>
 * </ul>
 *
 * <p>
 * Amounts are unrounded; rounding is left to the caller.
 * </p>
 */
//@formatter:on
public final class SecurityPeriodSeries {

  private SecurityPeriodSeries() {
  }

  /**
   * Position of the security on one trading day, summed over the holding rows of the scope.
   *
   * @param date          the trading day
   * @param unitsAsTraded units held as traded on that day, without margin rows
   * @param valueSC       value in security currency; for a margin instrument the open result
   * @param valueMC       the same value converted with the closing rate of that day
   * @param negativeUnits whether a non-margin row of that day has negative units
   * @param notValued     whether a held row has no usable price on that day
   * @param missingFx     whether a held row lacks the closing rate into the main currency on that day
   */
  public record DailyValue(LocalDate date, double unitsAsTraded, double valueSC, double valueMC,
      boolean negativeUnits, boolean notValued, boolean missingFx) {
  }

  /**
   * Span of one holding row with units.
   *
   * @param from first day held at the close
   * @param to   last day held at the close, null while the row is open
   */
  public record Span(LocalDate from, LocalDate to) {
  }

  /**
   * Flow of one transaction into or out of the position, as recorded by the replay.
   *
   * @param transactionDate    business date of the transaction
   * @param flowSC             amount in security currency, positive into the position
   * @param idSecurityTransfer the security transfer the transaction belongs to, null for an ordinary transaction
   * @param idSpinOffApp       the spin-off application of a parent sale or repurchase, null for every other
   *                           transaction. The two parent legs together move only the market value of the child out of
   *                           the position and are netted into one flow.
   */
  public record Flow(LocalDate transactionDate, double flowSC, Integer idSecurityTransfer, Integer idSpinOffApp) {

    public Flow(LocalDate transactionDate, double flowSC, Integer idSecurityTransfer) {
      this(transactionDate, flowSC, idSecurityTransfer, null);
    }
  }

  /**
   * Everything the calculation of one security needs.
   *
   * @param dateFrom         excluded base date of the period, a valuation day of the scope
   * @param dateTo           last date of the period, a valuation day of the scope
   * @param tradingDays      the days of {@code trading_days_plus} from {@code dateFrom} to {@code dateTo}, ascending
   * @param marginInstrument whether the security is a margin instrument
   * @param dailyValues      the valued days of the security; days without a holding row are absent
   * @param spans            the holding rows with units of the security that overlap the period
   * @param flows            the flows of the security; flows outside the period are ignored
   * @param rateOfDay        closing rate from the security currency into the main currency, 1 for a security in the
   *                         main currency, null when the rate of the day is missing
   */
  public record Input(LocalDate dateFrom, LocalDate dateTo, List<LocalDate> tradingDays, boolean marginInstrument,
      List<DailyValue> dailyValues, List<Span> spans, List<Flow> flows, Function<LocalDate, Double> rateOfDay) {
  }

  /**
   * Figures of one security. A figure that cannot be formed is null; {@link #remark()} says why.
   *
   * @param holdingDays         calendar days after {@code dateFrom} up to {@code dateTo} held at the close
   * @param unitsStart          units as traded on {@code dateFrom}, null for a margin instrument
   * @param unitsEnd            units as traded on {@code dateTo}, null for a margin instrument
   * @param valueStartMC        value on {@code dateFrom} in main currency
   * @param valueEndMC          value on {@code dateTo} in main currency
   * @param inflowMC            sum of the inflows of the period in main currency
   * @param outflowMC           sum of the outflows of the period in main currency, as a positive amount
   * @param gainPeriod          gain of the period in security currency
   * @param gainPeriodMC        gain of the period in main currency
   * @param twr                 time-weighted return in main currency as a ratio
   * @param twrSecurityCurrency time-weighted return in security currency as a ratio
   * @param remark              cause of missing figures, null for a complete row
   */
  public record Result(int holdingDays, Double unitsStart, Double unitsEnd, Double valueStartMC, Double valueEndMC,
      Double inflowMC, Double outflowMC, Double gainPeriod, Double gainPeriodMC, Double twr, Double twrSecurityCurrency,
      SecurityPeriodRemark remark) {
  }

  /** Inflow and outflow of one placement day in both currencies; the outflows are positive amounts. */
  private static final class DayFlow {
    double inflowSC;
    double outflowSC;
    double inflowMC;
    double outflowMC;
  }

  /** Flows of the period, netted and placed on their grid days. */
  private record PlacedFlows(Map<LocalDate, DayFlow> byDay, double sumSC, double inflowMC, double outflowMC,
      boolean missingRate) {
  }

  /**
   * Key that nets the legs of one security transfer, and the parent sale and repurchase of one spin-off, on one day
   * and keeps every other flow apart.
   */
  private record NettingKey(LocalDate day, Integer idSecurityTransfer, Integer idSpinOffApp, int ordinal) {
  }

  /**
   * Calculates the figures of one security.
   *
   * @param input the values, spans, flows and rates of the security
   * @return the figures, unrounded
   */
  public static Result calculate(Input input) {
    Map<LocalDate, DailyValue> values = new HashMap<>();
    input.dailyValues().forEach(dv -> values.put(dv.date(), dv));
    List<LocalDate> grid = grid(input.tradingDays(), values);
    Set<LocalDate> gridSet = new HashSet<>(grid);
    boolean endpointsValued = gridSet.contains(input.dateFrom()) && gridSet.contains(input.dateTo());
    PlacedFlows placed = placeFlows(input, grid);
    int holdingDays = holdingDays(input.spans(), input.dateFrom(), input.dateTo());
    Double unitsStart = input.marginInstrument() ? null : unitsOn(values, input.dateFrom());
    Double unitsEnd = input.marginInstrument() ? null : unitsOn(values, input.dateTo());
    Double inflowMC = placed.missingRate() ? null : placed.inflowMC();
    Double outflowMC = placed.missingRate() ? null : placed.outflowMC();
    if (!endpointsValued) {
      return new Result(holdingDays, unitsStart, unitsEnd, null, null, inflowMC, outflowMC, null, null, null, null,
          SecurityPeriodRemark.MISSING_VALUATION);
    }
    DailyValue start = values.get(input.dateFrom());
    DailyValue end = values.get(input.dateTo());
    double valueStartSC = start == null ? 0 : start.valueSC();
    double valueEndSC = end == null ? 0 : end.valueSC();
    double valueStartMC = start == null ? 0 : start.valueMC();
    double valueEndMC = end == null ? 0 : end.valueMC();
    double gainPeriod = valueEndSC - valueStartSC - placed.sumSC();
    Double gainPeriodMC = placed.missingRate() ? null : valueEndMC - valueStartMC - placed.inflowMC()
        + placed.outflowMC();

    boolean negativeUnits = input.dailyValues().stream().anyMatch(DailyValue::negativeUnits);
    Double twrSecurityCurrency = null;
    Double twr = null;
    if (!input.marginInstrument() && !negativeUnits) {
      twrSecurityCurrency = ReturnSeries.of(points(grid, values, placed, false), Convention.REPORT).totalReturn();
      if (!placed.missingRate()) {
        twr = ReturnSeries.of(points(grid, values, placed, true), Convention.REPORT).totalReturn();
      }
    }
    SecurityPeriodRemark remark = remark(input.marginInstrument(), placed.missingRate(), negativeUnits,
        twrSecurityCurrency);
    return new Result(holdingDays, unitsStart, unitsEnd, valueStartMC, valueEndMC, inflowMC, outflowMC, gainPeriod,
        gainPeriodMC, twr, twrSecurityCurrency, remark);
  }

  /** Picks the first remark that applies, in the order of {@link SecurityPeriodRemark}. */
  private static SecurityPeriodRemark remark(boolean marginInstrument, boolean missingRate, boolean negativeUnits,
      Double twrSecurityCurrency) {
    if (missingRate) {
      return SecurityPeriodRemark.MISSING_EXCHANGE_RATE;
    }
    if (marginInstrument) {
      return SecurityPeriodRemark.MARGIN_GAIN_ONLY;
    }
    if (negativeUnits) {
      return SecurityPeriodRemark.NEGATIVE_UNITS;
    }
    return twrSecurityCurrency == null ? SecurityPeriodRemark.NO_CAPITAL_BASE : null;
  }

  /** Trading days without the days on which the security is held but has no price or no rate. */
  private static List<LocalDate> grid(List<LocalDate> tradingDays, Map<LocalDate, DailyValue> values) {
    List<LocalDate> grid = new ArrayList<>(tradingDays.size());
    for (LocalDate day : tradingDays) {
      DailyValue dv = values.get(day);
      if (dv == null || !(dv.notValued() || dv.missingFx())) {
        grid.add(day);
      }
    }
    return grid;
  }

  /**
   * Places the flows of the period on their grid days, nets the legs of a security transfer and converts the netted
   * amounts with the rate of the placement day. A flow after the last grid day, possible only when {@code dateTo} is
   * not valued, is kept on {@code dateTo} so that the sums stay complete.
   */
  private static PlacedFlows placeFlows(Input input, List<LocalDate> grid) {
    Map<NettingKey, Double> netted = new LinkedHashMap<>();
    int ordinal = 0;
    for (Flow flow : input.flows()) {
      LocalDate date = flow.transactionDate();
      if (date.isAfter(input.dateFrom()) && !date.isAfter(input.dateTo())) {
        LocalDate day = firstOnOrAfter(grid, date, input.dateTo());
        NettingKey key;
        if (flow.idSecurityTransfer() != null) {
          key = new NettingKey(day, flow.idSecurityTransfer(), null, -1);
        } else if (flow.idSpinOffApp() != null) {
          key = new NettingKey(day, null, flow.idSpinOffApp(), -1);
        } else {
          key = new NettingKey(day, null, null, ordinal++);
        }
        netted.merge(key, flow.flowSC(), Double::sum);
      }
    }
    Map<LocalDate, DayFlow> byDay = new TreeMap<>();
    double sumSC = 0;
    double inflowMC = 0;
    double outflowMC = 0;
    boolean missingRate = false;
    for (Map.Entry<NettingKey, Double> entry : netted.entrySet()) {
      double amount = entry.getValue();
      sumSC += amount;
      if (amount == 0) {
        continue;
      }
      LocalDate day = entry.getKey().day();
      Double rate = input.rateOfDay().apply(day);
      missingRate |= rate == null;
      double amountMC = rate == null ? 0 : amount * rate;
      DayFlow dayFlow = byDay.computeIfAbsent(day, d -> new DayFlow());
      if (amount > 0) {
        dayFlow.inflowSC += amount;
        dayFlow.inflowMC += amountMC;
        inflowMC += amountMC;
      } else {
        dayFlow.outflowSC -= amount;
        dayFlow.outflowMC -= amountMC;
        outflowMC -= amountMC;
      }
    }
    return new PlacedFlows(byDay, sumSC, inflowMC, outflowMC, missingRate);
  }

  /** First grid day on or after the given date, or the fallback when the grid ends before it. */
  private static LocalDate firstOnOrAfter(List<LocalDate> grid, LocalDate date, LocalDate fallback) {
    int index = Collections.binarySearch(grid, date);
    if (index < 0) {
      index = -index - 1;
    }
    return index < grid.size() ? grid.get(index) : fallback;
  }

  /** Valuation points of the grid in main or security currency. */
  private static List<ValuationPoint> points(List<LocalDate> grid, Map<LocalDate, DailyValue> values,
      PlacedFlows placed, boolean mainCurrency) {
    List<ValuationPoint> points = new ArrayList<>(grid.size());
    for (LocalDate day : grid) {
      DailyValue dv = values.get(day);
      DayFlow flow = placed.byDay().get(day);
      double value = dv == null ? 0 : (mainCurrency ? dv.valueMC() : dv.valueSC());
      double inflow = flow == null ? 0 : (mainCurrency ? flow.inflowMC : flow.inflowSC);
      double outflow = flow == null ? 0 : (mainCurrency ? flow.outflowMC : flow.outflowSC);
      // regular only steers the volatility, which this view does not compute
      points.add(new ValuationPoint(day, value, inflow, outflow, true));
    }
    return points;
  }

  private static double unitsOn(Map<LocalDate, DailyValue> values, LocalDate day) {
    DailyValue dv = values.get(day);
    return dv == null ? 0 : dv.unitsAsTraded();
  }

  /**
   * Calendar days after {@code dateFrom} up to and including {@code dateTo} covered by at least one span, each day
   * counted once however many spans cover it.
   *
   * @param spans    the holding rows with units
   * @param dateFrom excluded first day
   * @param dateTo   included last day
   * @return the number of days held at the close
   */
  static int holdingDays(List<Span> spans, LocalDate dateFrom, LocalDate dateTo) {
    long first = dateFrom.toEpochDay() + 1;
    long last = dateTo.toEpochDay();
    List<long[]> ranges = new ArrayList<>();
    for (Span span : spans) {
      long start = Math.max(span.from().toEpochDay(), first);
      long end = Math.min(span.to() == null ? last : span.to().toEpochDay(), last);
      if (start <= end) {
        ranges.add(new long[] { start, end });
      }
    }
    ranges.sort(Comparator.comparingLong(r -> r[0]));
    long days = 0;
    long coveredUntil = Long.MIN_VALUE;
    for (long[] range : ranges) {
      long start = Math.max(range[0], coveredUntil + 1);
      if (start <= range[1]) {
        days += range[1] - start + 1;
        coveredUntil = range[1];
      }
    }
    return (int) days;
  }
}
