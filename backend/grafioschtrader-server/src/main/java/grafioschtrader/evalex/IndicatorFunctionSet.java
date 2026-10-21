package grafioschtrader.evalex;

import static grafioschtrader.types.IndicatorCellStatus.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.IntToDoubleFunction;
import java.util.stream.Collectors;

import com.ezylang.evalex.Expression;
import com.ezylang.evalex.config.ExpressionConfiguration;
import com.ezylang.evalex.data.EvaluationValue;
import com.ezylang.evalex.functions.AbstractFunction;
import com.ezylang.evalex.functions.FunctionIfc;
import com.ezylang.evalex.functions.FunctionParameter;
import com.ezylang.evalex.parser.Token;

import grafioschtrader.dto.IDateCloseHighLow;

/** Request-local functions and caches, shared by columns with the same instrument and benchmark. Not thread safe. */
public final class IndicatorFunctionSet {
  private final IndicatorSeries data;
  private final Map<String, FunctionIfc> functions = new LinkedHashMap<>();
  private List<IDateCloseHighLow> common;
  private List<IDateCloseHighLow> benchmark;
  private ExpressionConfiguration configuration;

  public IndicatorFunctionSet(IndicatorSeries data) {
    this.data = data;
    functions.put("SMA", new SmaFunction(data.series()));
    functions.put("EMA", new EmaFunction(data.series()));
    functions.put("RSI", new RsiFunction(data.series()));
    for (String name : List.of("CLOSE", "HIGHEST", "LOWEST", "ZSCORE", "VOLA", "MAXDD", "ATR", "RS", "BETA",
        "DIVGROWTH"))
      functions.put(name, new PeriodFunction(n -> compute(name, n)));
    functions.put("DIVYIELD", new ZeroFunction(() -> dividendYield()));
    functions.put("DIVYEARS", new ZeroFunction(() -> dividendYears()));
  }

  public Map<String, FunctionIfc> functions() {
    return Collections.unmodifiableMap(functions);
  }

  /**
   * The EvalEx configuration with these functions, built on first use. Building the default configuration creates every
   * EvalEx function by reflection, so the columns of one instrument share it instead of building one per cell.
   */
  public ExpressionConfiguration configuration() {
    if (configuration == null) {
      configuration = ExpressionConfiguration.defaultConfiguration();
      for (var function : functions.entrySet())
        configuration = configuration.withAdditionalFunctions(function);
    }
    return configuration;
  }

  public LocalDate lastCommonDate() {
    align();
    return common.isEmpty() ? null : common.getLast().getDate();
  }

  private double compute(String name, int n) {
    return switch (name) {
    case "RS", "BETA" -> relative(name, n);
    case "DIVGROWTH" -> dividendGrowth(n);
    case "ATR" -> atr(n);
    default -> window(name, n);
    };
  }

  private double window(String name, int n) {
    var series = data.series();
    int needed = Set.of("HIGHEST", "LOWEST", "ZSCORE").contains(name) ? n : n + 1;
    requireRows(series, needed);
    var rows = series.subList(series.size() - needed, series.size());
    if (name.equals("CLOSE"))
      return rows.getFirst().getClose();
    if (name.equals("HIGHEST"))
      return rows.stream().mapToDouble(IDateCloseHighLow::getClose).max().orElseThrow();
    if (name.equals("LOWEST"))
      return rows.stream().mapToDouble(IDateCloseHighLow::getClose).min().orElseThrow();
    if (name.equals("ZSCORE"))
      return zscore(rows);
    rows.forEach(r -> positive(r.getClose()));
    if (name.equals("MAXDD")) {
      double peak = rows.getFirst().getClose(), drawdown = 0;
      for (var row : rows) {
        peak = Math.max(peak, row.getClose());
        drawdown = Math.max(drawdown, (peak - row.getClose()) / peak);
      }
      return drawdown * 100;
    }
    double[] returns = new double[n];
    for (int i = 0; i < n; i++)
      returns[i] = Math.log(rows.get(i + 1).getClose() / rows.get(i).getClose());
    double mean = Arrays.stream(returns).average().orElseThrow();
    return Math.sqrt(Arrays.stream(returns).map(r -> (r - mean) * (r - mean)).sum() / (n - 1)) * Math.sqrt(252) * 100;
  }

  /** Population z-score, also used by the mean-reversion strategy. */
  public static double zscore(List<? extends IDateCloseHighLow> rows) {
    double mean = rows.stream().mapToDouble(IDateCloseHighLow::getClose).average().orElseThrow();
    double variance = rows.stream().mapToDouble(r -> Math.pow(r.getClose() - mean, 2)).average().orElseThrow();
    if (variance <= 0 || !Double.isFinite(variance))
      throw new IndicatorUnavailableException(INVALID_DATA);
    return (rows.getLast().getClose() - mean) / Math.sqrt(variance);
  }

  private double atr(int n) {
    var rows = data.series();
    int first = rows.size();
    while (first > 0 && rows.get(first - 1).getHigh() != null && rows.get(first - 1).getLow() != null)
      first--;
    requireRows(rows.subList(first, rows.size()), n + 1);
    double value = 0;
    for (int i = first + 1; i < rows.size(); i++) {
      var row = rows.get(i);
      double previous = rows.get(i - 1).getClose();
      double range = Math.max(row.getHigh() - row.getLow(),
          Math.max(Math.abs(row.getHigh() - previous), Math.abs(row.getLow() - previous)));
      if (!Double.isFinite(range) || row.getHigh() < row.getLow())
        throw new IndicatorUnavailableException(INVALID_DATA);
      if (i <= first + n)
        value += range / n;
      else
        value = (value * (n - 1) + range) / n;
    }
    return value;
  }

  private void align() {
    if (common != null)
      return;
    var byDate = data.benchmark().stream().collect(Collectors.toMap(IDateCloseHighLow::getDate, r -> r));
    common = new ArrayList<>();
    benchmark = new ArrayList<>();
    for (var row : data.series()) {
      var other = byDate.get(row.getDate());
      if (other != null) {
        common.add(row);
        benchmark.add(other);
      }
    }
  }

  private double relative(String name, int n) {
    align();
    requireRows(common, n + 1);
    int start = common.size() - n - 1, last = common.size() - 1;
    if (name.equals("RS")) {
      return (positive(common.get(last).getClose()) / positive(common.get(start).getClose())
          / (positive(benchmark.get(last).getClose()) / positive(benchmark.get(start).getClose())) - 1) * 100;
    }
    double[] a = new double[n], b = new double[n];
    for (int i = 0; i < n; i++) {
      a[i] = positive(common.get(start + i + 1).getClose()) / positive(common.get(start + i).getClose()) - 1;
      b[i] = positive(benchmark.get(start + i + 1).getClose()) / positive(benchmark.get(start + i).getClose()) - 1;
    }
    double ma = Arrays.stream(a).average().orElseThrow(), mb = Arrays.stream(b).average().orElseThrow();
    double covariance = 0, variance = 0;
    for (int i = 0; i < n; i++) {
      covariance += (a[i] - ma) * (b[i] - mb);
      variance += Math.pow(b[i] - mb, 2);
    }
    if (variance == 0)
      throw new IndicatorUnavailableException(INVALID_DATA);
    return covariance / variance;
  }

  private void checkDividends() {
    if (!data.security())
      throw new IndicatorUnavailableException(NOT_APPLICABLE);
    if (!data.dividendDataKnown())
      throw new IndicatorUnavailableException(NO_DIVIDEND_DATA);
  }

  private double dividendSum(LocalDate from, LocalDate to) {
    checkDividends();
    double sum = 0;
    for (var dividend : data.dividends()) {
      if (dividend.exDate().isBefore(from) || dividend.exDate().isAfter(to)
          || dividend.exDate().isAfter(data.asOfDate()))
        continue;
      if (dividend.amount() == null)
        throw new IndicatorUnavailableException(NO_FX_RATE);
      if (dividend.amount() < 0 || !Double.isFinite(dividend.amount()))
        throw new IndicatorUnavailableException(INVALID_DATA);
      sum += dividend.amount();
    }
    return sum;
  }

  private double year(int year) {
    return dividendSum(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
  }

  private double dividendYield() {
    checkDividends();
    if (data.price() == null)
      throw new IndicatorUnavailableException(NO_PRICE);
    return dividendSum(data.asOfDate().minusDays(364), data.asOfDate()) / positive(data.price()) * 100;
  }

  private double dividendGrowth(int n) {
    int year = data.asOfDate().getYear() - 1;
    return (Math.pow(positive(year(year)) / positive(year(year - n)), 1.0 / n) - 1) * 100;
  }

  private double dividendYears() {
    checkDividends();
    int count = 0;
    for (int y = data.asOfDate().getYear() - 1; count < 20; y--) {
      double amount = year(y), previous = year(y - 1);
      if (amount <= 0 || amount < previous)
        break;
      count++;
    }
    return count;
  }

  private static void requireRows(List<?> rows, int needed) {
    if (rows.size() < needed)
      throw new IndicatorUnavailableException(NO_HISTORY);
  }

  private static double positive(double value) {
    if (value <= 0 || !Double.isFinite(value))
      throw new IndicatorUnavailableException(INVALID_DATA);
    return value;
  }

  @FunctionParameter(name = "period")
  public static final class PeriodFunction extends AbstractFunction {
    private final IntToDoubleFunction calculation;
    private final Map<Integer, Double> cache = new HashMap<>();

    PeriodFunction(IntToDoubleFunction calculation) {
      this.calculation = calculation;
    }

    @Override
    public EvaluationValue evaluate(Expression expression, Token token, EvaluationValue... parameters) {
      int n = parameters[0].getNumberValue().intValueExact();
      return EvaluationValue.numberValue(BigDecimal.valueOf(cache.computeIfAbsent(n, calculation::applyAsDouble)));
    }
  }

  public static final class ZeroFunction extends AbstractFunction {
    private final java.util.function.DoubleSupplier calculation;
    private Double cached;

    ZeroFunction(java.util.function.DoubleSupplier calculation) {
      this.calculation = calculation;
    }

    @Override
    public EvaluationValue evaluate(Expression expression, Token token, EvaluationValue... parameters) {
      if (cached == null)
        cached = calculation.getAsDouble();
      return EvaluationValue.numberValue(BigDecimal.valueOf(cached));
    }
  }
}
