package grafioschtrader.reports;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.ToIntFunction;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import grafioschtrader.dto.SeasonalReturnsResult;
import grafioschtrader.dto.SeasonalReturnsResult.SeasonalColumnStat;
import grafioschtrader.dto.SeasonalReturnsResult.SeasonalYearRow;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.entities.projection.SecurityPeriodClose;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.service.RiskFreeRateService;
import grafioschtrader.types.SeasonalPeriodType;

/**
 * Builds a seasonality heat map for a single security or currency pair: a matrix of period returns with the years as
 * rows and the calendar months or quarters as columns, plus a trailing annual-return column and per-column footer
 * statistics.
 *
 * <p>
 * Returns are derived from month-end closes ({@link SecurityPeriodClose}). The same monthly dataset feeds the monthly,
 * quarterly and annual aggregations: a quarter/year return uses the close of the last available month-end in the
 * period, and dividends with an ex-date in the period are summed in. Currency conversion into the tenant main currency
 * reuses the instrument/currency-pair resolution of {@link InstrumentStatisticsSummary}.
 * </p>
 */
public class SeasonalReturnsReport {

  private final SecurityJpaRepository securityJpaRepository;
  private final TenantJpaRepository tenantJpaRepository;
  private final CurrencypairJpaRepository currencypairJpaRepository;
  private final RiskFreeRateService riskFreeRateService;

  public SeasonalReturnsReport(SecurityJpaRepository securityJpaRepository, TenantJpaRepository tenantJpaRepository,
      CurrencypairJpaRepository currencypairJpaRepository, RiskFreeRateService riskFreeRateService) {
    this.securityJpaRepository = securityJpaRepository;
    this.tenantJpaRepository = tenantJpaRepository;
    this.currencypairJpaRepository = currencypairJpaRepository;
    this.riskFreeRateService = riskFreeRateService;
  }

  /**
   * Calculates the seasonality heat map for the given instrument.
   *
   * @param idSecuritycurrency the security or currency pair to analyse
   * @param periodType         column granularity (monthly or quarterly)
   * @param includeDividends   when true, dividends/interest are added to the period return
   * @param inTenantCurrency   when true, returns are converted into the tenant main currency (ignored when no
   *                           conversion is available)
   * @return the populated result, with empty rows when the instrument has no price history
   */
  public SeasonalReturnsResult calculate(Integer idSecuritycurrency, SeasonalPeriodType periodType,
      boolean includeDividends, boolean inTenantCurrency) {
    InstrumentStatisticsSummary iss = new InstrumentStatisticsSummary(securityJpaRepository, tenantJpaRepository,
        currencypairJpaRepository, riskFreeRateService);
    iss.prepareSecurityCurrencypairs(idSecuritycurrency);

    boolean currencyConversionAvailable = !iss.isSecurityTenantSameCurrency();
    boolean convert = inTenantCurrency && currencyConversionAvailable;
    boolean divide = convert && iss.isCurrencyDivide();

    List<SecurityPeriodClose> rawList = convert
        ? securityJpaRepository.getSecurityMonthDivSumCurrencyClose(idSecuritycurrency,
            iss.getCurrencypairs().getFirst().getIdSecuritycurrency())
        : securityJpaRepository.getSecurityMonthCloseDivSum(idSecuritycurrency);

    boolean dividendsAvailable = rawList.stream().anyMatch(p -> p.getPeriodDiv() != 0.0);
    SeasonalReturnsResult result = new SeasonalReturnsResult(periodType,
        currencyLabel(iss, convert), includeDividends && dividendsAvailable, convert, dividendsAvailable,
        currencyConversionAvailable);

    List<Point> points = buildPoints(rawList, convert, divide, includeDividends && dividendsAvailable);
    if (points.isEmpty()) {
      return result;
    }

    int columns = periodType.getNumberOfColumns();
    ToIntFunction<Point> colFn = periodType == SeasonalPeriodType.MONTHLY ? p -> p.month - 1
        : p -> (p.month - 1) / 3;
    Map<Integer, Map<Integer, Double>> periodReturns = computeReturns(aggregate(points, colFn));
    Map<Integer, Map<Integer, Double>> annualReturns = computeReturns(aggregate(points, p -> 0));

    fillYearRows(result, points, periodReturns, annualReturns, columns);
    fillColumnStats(result, periodReturns, annualReturns, columns);
    return result;
  }

  /** ISO currency the returns are expressed in. */
  private String currencyLabel(InstrumentStatisticsSummary iss, boolean convert) {
    if (convert) {
      return iss.getTenantCurrency();
    }
    Securitycurrency<?> sc = iss.getSecurityCurrency();
    return sc instanceof Currencypair cp ? cp.getToCurrency() : iss.getCurrencyOfSecurity();
  }

  /** Converts the raw projections into currency-adjusted month points. */
  private List<Point> buildPoints(List<SecurityPeriodClose> rawList, boolean convert, boolean divide,
      boolean includeDividends) {
    List<Point> points = new ArrayList<>();
    for (SecurityPeriodClose pc : rawList) {
      double factor = !convert ? 1.0 : divide ? 1.0 / pc.getCurrencyClose() : pc.getCurrencyClose();
      double value = pc.getSecurityClose() * factor;
      double div = includeDividends ? pc.getPeriodDiv() * factor : 0.0;
      points.add(new Point(pc.getDate(), value, div));
    }
    points.sort(Comparator.comparing(p -> p.date));
    return points;
  }

  /**
   * Aggregates month points into period-end points grouped by (year, column). The end value is the value of the last
   * available month in the group; dividends are summed over the whole group.
   */
  private List<Agg> aggregate(List<Point> ascPoints, ToIntFunction<Point> colFn) {
    Map<String, Agg> map = new LinkedHashMap<>();
    for (Point p : ascPoints) {
      int col = colFn.applyAsInt(p);
      Agg agg = map.computeIfAbsent(p.year + ":" + col, k -> new Agg(p.year, col));
      agg.endDate = p.date;
      agg.value = p.value;
      agg.div += p.div;
    }
    List<Agg> aggs = new ArrayList<>(map.values());
    aggs.sort(Comparator.comparing(a -> a.endDate));
    return aggs;
  }

  /** Period return of each aggregate against its chronological predecessor, as year -> column -> percent. */
  private Map<Integer, Map<Integer, Double>> computeReturns(List<Agg> ordered) {
    Map<Integer, Map<Integer, Double>> result = new HashMap<>();
    for (int i = 1; i < ordered.size(); i++) {
      Agg cur = ordered.get(i);
      Agg prev = ordered.get(i - 1);
      if (prev.value != 0.0) {
        double ret = ((cur.value + cur.div) / prev.value - 1.0) * 100.0;
        result.computeIfAbsent(cur.year, y -> new HashMap<>()).put(cur.col, round2(ret));
      }
    }
    return result;
  }

  private void fillYearRows(SeasonalReturnsResult result, List<Point> points,
      Map<Integer, Map<Integer, Double>> periodReturns, Map<Integer, Map<Integer, Double>> annualReturns,
      int columns) {
    TreeSet<Integer> years = new TreeSet<>(java.util.Collections.reverseOrder());
    points.forEach(p -> years.add(p.year));
    for (int year : years) {
      Map<Integer, Double> cols = periodReturns.getOrDefault(year, Map.of());
      List<Double> values = new ArrayList<>(columns);
      int present = 0;
      for (int c = 0; c < columns; c++) {
        Double v = cols.get(c);
        values.add(v);
        if (v != null) {
          present++;
        }
      }
      Map<Integer, Double> annualCol = annualReturns.get(year);
      Double annual = annualCol == null ? null : annualCol.get(0);
      result.yearRows.add(new SeasonalYearRow(year, values, annual, present < columns));
    }
  }

  private void fillColumnStats(SeasonalReturnsResult result, Map<Integer, Map<Integer, Double>> periodReturns,
      Map<Integer, Map<Integer, Double>> annualReturns, int columns) {
    for (int c = 0; c < columns; c++) {
      final int col = c;
      List<Double> values = periodReturns.values().stream().map(m -> m.get(col)).filter(v -> v != null).toList();
      result.columnStats.add(columnStat(values));
    }
    List<Double> annualValues = annualReturns.values().stream().map(m -> m.get(0)).filter(v -> v != null).toList();
    result.columnStats.add(columnStat(annualValues));
  }

  private SeasonalColumnStat columnStat(List<Double> values) {
    if (values.isEmpty()) {
      return new SeasonalColumnStat(null, null, null, null, 0);
    }
    DescriptiveStatistics ds = new DescriptiveStatistics();
    long positive = 0;
    for (double v : values) {
      ds.addValue(v);
      if (v > 0.0) {
        positive++;
      }
    }
    double pctPositive = positive * 100.0 / values.size();
    // Sample standard deviation is undefined (NaN) for a single value; NaN is not valid JSON, so report null.
    Double stdDev = values.size() < 2 ? null : round2(ds.getStandardDeviation());
    return new SeasonalColumnStat(round2(ds.getMean()), round2(ds.getPercentile(50)), stdDev, round2(pctPositive),
        values.size());
  }

  /** Rounds to two decimals; the heat map never shows more precision, so this trims the JSON payload. */
  private static double round2(double value) {
    return Math.round(value * 100.0) / 100.0;
  }

  /** A single currency-adjusted month-end observation. */
  private static class Point {
    private final LocalDate date;
    private final int year;
    private final int month;
    private final double value;
    private final double div;

    private Point(LocalDate date, double value, double div) {
      this.date = date;
      this.year = date.getYear();
      this.month = date.getMonthValue();
      this.value = value;
      this.div = div;
    }
  }

  /** A period-end aggregate (month, quarter or year) for one (year, column). */
  private static class Agg {
    private final int year;
    private final int col;
    private LocalDate endDate;
    private double value;
    private double div;

    private Agg(int year, int col) {
      this.year = year;
      this.col = col;
    }
  }
}
