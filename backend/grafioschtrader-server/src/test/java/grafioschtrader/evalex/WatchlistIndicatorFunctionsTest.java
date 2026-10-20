package grafioschtrader.evalex;

import static grafioschtrader.types.IndicatorCellStatus.*;
import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.util.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.entities.Historyquote;
import grafioschtrader.evalex.IndicatorSeries.DividendAmount;
import grafioschtrader.ta.indicator.calc.RelativeStrengthIndex;
import grafioschtrader.types.IndicatorResultType;

/** Independently calculated examples of financial results and their unavailable-data boundaries. */
class WatchlistIndicatorFunctionsTest {
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);

  private static List<Historyquote> prices(double... values) {
    var rows = new ArrayList<Historyquote>();
    for (int i = 0; i < values.length; i++) {
      var row = new Historyquote(i, TODAY.minusDays(values.length - i - 1), values[i]);
      row.setHigh(values[i] + 2);
      row.setLow(values[i] - 1);
      rows.add(row);
    }
    return rows;
  }

  private static double value(String expression, IndicatorFunctionSet functions) throws Exception {
    AlertExpressionSupport.validate(expression, EnumSet.allOf(IndicatorData.class));
    return AlertExpressionSupport.create(expression, functions).evaluate().getNumberValue().doubleValue();
  }

  private static IndicatorFunctionSet set(List<Historyquote> rows) {
    return new IndicatorFunctionSet(IndicatorSeries.history(rows));
  }

  private static IndicatorFunctionSet dividends(LocalDate today, boolean security, boolean known,
      DividendAmount... amounts) {
    return new IndicatorFunctionSet(
        new IndicatorSeries(List.of(), List.of(), List.of(amounts), known, security, today, 100.0));
  }

  private static void unavailable(String expression, IndicatorFunctionSet set,
      grafioschtrader.types.IndicatorCellStatus status) {
    assertThatThrownBy(() -> value(expression, set)).isInstanceOfSatisfying(IndicatorUnavailableException.class,
        error -> assertThat(error.getStatus()).isEqualTo(status));
  }

  @Test
  @DisplayName("Window functions use closes, population z-score, sample log volatility and unrecovered drawdowns")
  void windowValues() throws Exception {
    var functions = set(prices(100, 120, 90, 99));
    assertThat(value("CLOSE(0)", functions)).isEqualTo(99);
    assertThat(value("CLOSE(3)", functions)).isEqualTo(100);
    assertThat(value("HIGHEST(3)", functions)).isEqualTo(120);
    assertThat(value("LOWEST(3)", functions)).isEqualTo(90);
    // Last three values: mean 103, squared deviations 289 + 169 + 16.
    assertThat(value("ZSCORE(3)", functions)).isCloseTo(-4 / Math.sqrt(158), within(1e-12));
    assertThat(value("MAXDD(3)", functions)).isEqualTo(25);
    double a = Math.log(1.2), b = Math.log(.75), c = Math.log(1.1), mean = (a + b + c) / 3;
    double volatility = Math.sqrt(((a - mean) * (a - mean) + (b - mean) * (b - mean) + (c - mean) * (c - mean)) / 2)
        * Math.sqrt(252) * 100;
    assertThat(value("VOLA(3)", functions)).isCloseTo(volatility, within(1e-10));
    unavailable("CLOSE(4)", functions, NO_HISTORY);
    unavailable("ZSCORE(3)", set(prices(5, 5, 5)), INVALID_DATA);
    unavailable("VOLA(2)", set(prices(5, 0, 6)), INVALID_DATA);
    unavailable("MAXDD(2)", set(prices(5, -1, 6)), INVALID_DATA);
  }

  @Test
  @DisplayName("Existing smoothers retain their seeds and ATR restarts after missing high/low data")
  void smoothing() throws Exception {
    var rows = prices(10, 12, 11, 14, 13);
    var functions = set(rows);
    assertThat(value("SMA(3)", functions)).isCloseTo(38.0 / 3, within(1e-12));
    // First-close seed 10, then alpha=.5: 11, 11, 12.5 and 12.75.
    assertThat(value("EMA(3)", functions)).isCloseTo(12.75, within(1e-12));
    // TR: 4,3,5,3. Seed (4+3)/2=3.5, Wilder: 4.25,3.625.
    assertThat(value("ATR(2)", functions)).isEqualTo(3.625);
    assertThat(value("RSI(2)", functions)).isCloseTo(100 - 100 / (1 + 1 / .625), within(1e-9));
    var rsi = new RelativeStrengthIndex(2, rows.size());
    rows.forEach(r -> rsi.addData(r.getDate(), r.getClose()));
    var points = rsi.getTaIndicatorData();
    assertThat(value("RSI(2)", functions)).isCloseTo(points[points.length - 1].value, within(1e-9));
    rows.get(2).setHigh(null);
    unavailable("ATR(2)", set(rows), NO_HISTORY);
    rows.getLast().setLow(null);
    unavailable("ATR(1)", set(rows), NO_HISTORY);
    assertThat(value("SMA(3)", set(rows))).isCloseTo(38.0 / 3, within(1e-12));
  }

  @Test
  @DisplayName("Benchmark returns align dates before taking periods")
  void benchmark() throws Exception {
    var rows = prices(100, 110, 777, 132);
    var benchmark = prices(100, 105, 999, 115.5);
    benchmark.remove(2); // common dates have returns .10,.20 and .05,.10 => beta 2
    var functions = new IndicatorFunctionSet(
        new IndicatorSeries(rows, benchmark, List.of(), false, true, TODAY, 132.0));
    assertThat(value("RS(2)", functions)).isCloseTo((1.32 / 1.155 - 1) * 100, within(1e-10));
    assertThat(value("BETA(2)", functions)).isCloseTo(2, within(1e-12));
    assertThat(functions.lastCommonDate()).isEqualTo(TODAY);
    unavailable("BETA(3)", functions, NO_HISTORY);
    var flat = new IndicatorFunctionSet(
        new IndicatorSeries(rows, prices(5, 5, 5, 5), List.of(), false, true, TODAY, 1.0));
    unavailable("BETA(2)", flat, INVALID_DATA);
  }

  @Test
  @DisplayName("Dividend windows exclude future ex-dates and preserve missing FX and missing-feed distinctions")
  void dividendYield() throws Exception {
    var converted = new DividendAmount(TODAY.minusDays(5), 2.0 * .9);
    var functions = dividends(TODAY, true, true, converted, new DividendAmount(TODAY.minusDays(365), 100.0),
        new DividendAmount(TODAY.plusDays(1), 50.0));
    assertThat(value("DIVYIELD()", functions)).isCloseTo(1.8, within(1e-12));
    unavailable("DIVYIELD()", dividends(TODAY, true, true, new DividendAmount(TODAY, null)), NO_FX_RATE);
    unavailable("DIVYIELD()", dividends(TODAY, true, true, new DividendAmount(TODAY, -1.0)), INVALID_DATA);
    unavailable("DIVYIELD()", dividends(TODAY, true, false), NO_DIVIDEND_DATA);
    unavailable("DIVYEARS()", dividends(TODAY, false, false), NOT_APPLICABLE);
    assertThat(value("DIVYIELD()", dividends(TODAY, true, true))).isZero();
  }

  @Test
  @DisplayName("Dividend growth ignores intervening zero years; streaks use completed years and stop at cuts")
  void dividendYears() throws Exception {
    DividendAmount[] amounts = { new DividendAmount(LocalDate.of(2022, 5, 1), 2.0),
        new DividendAmount(LocalDate.of(2024, 5, 1), 3.0), new DividendAmount(LocalDate.of(2025, 5, 1), 4.0),
        new DividendAmount(LocalDate.of(2026, 5, 1), 1.0) };
    assertThat(value("DIVGROWTH(3)", dividends(TODAY, true, true, amounts))).isCloseTo((Math.cbrt(2) - 1) * 100,
        within(1e-10));
    assertThat(value("DIVYEARS()", dividends(TODAY, true, true, amounts))).isEqualTo(2);
    assertThat(value("DIVYEARS()", dividends(LocalDate.of(2027, 1, 1), true, true, amounts))).isZero();
  }

  @Test
  @DisplayName("Static validation checks branches, context, literal argument ranges and types without input data")
  void validation() {
    assertThatThrownBy(() -> AlertExpressionSupport.validate("UNKNOWN(4)")).isInstanceOfSatisfying(
        AlertExpressionException.class, e -> assertThat(e.getKey()).isEqualTo("ALERT_EXPRESSION_UNKNOWN_FUNCTION"));
    assertThat(AlertExpressionSupport.resultType("IF(PRICE > 0, SMA(2)>3, CLOSE(0)<4)"))
        .isEqualTo(IndicatorResultType.BOOLEAN);
    assertThat(AlertExpressionSupport.resultType("IF(PRICE > 0, 1, 0)")).isEqualTo(IndicatorResultType.NUMBER);
    for (String invalid : List.of("IF(PRICE>0,1,TRUE)", "VOLA(1)", "BETA(1)", "DIVGROWTH(21)", "CLOSE(1000)",
        "SMA(1.5)"))
      assertThatThrownBy(() -> AlertExpressionSupport.validate(invalid, EnumSet.allOf(IndicatorData.class)))
          .isInstanceOf(AlertExpressionException.class);
    for (String unavailable : List.of("RS(2)", "BETA(2)", "DIVYIELD()", "DIVGROWTH(2)", "DIVYEARS()"))
      assertThatThrownBy(() -> AlertExpressionSupport.validate(unavailable)).isInstanceOfSatisfying(
          AlertExpressionException.class, e -> assertThat(e.getKey()).isEqualTo("ALERT_EXPRESSION_DATA_NOT_AVAILABLE"));
    for (String valid : List.of("close (0)", "HIGHEST(2)", "LOWEST(2)", "ZSCORE(2)", "VOLA(2)", "MAXDD(2)", "ATR(2)"))
      assertThatCode(() -> AlertExpressionSupport.validate(valid)).doesNotThrowAnyException();
  }

  private static List<String[]> csv(String name) throws Exception {
    try (var input = WatchlistIndicatorFunctionsTest.class.getResourceAsStream("/indicators/" + name);
        var reader = new java.io.BufferedReader(
            new java.io.InputStreamReader(Objects.requireNonNull(input), java.nio.charset.StandardCharsets.UTF_8))) {
      return reader.lines().skip(1).map(line -> line.split(",", -1)).toList();
    }
  }

  private static List<Historyquote> recordedPrices(String name) throws Exception {
    var result = new ArrayList<Historyquote>();
    for (var fields : csv(name)) {
      var quote = new Historyquote(1, LocalDate.parse(fields[0]), Double.parseDouble(fields[1]));
      quote.setHigh(fields[2].isEmpty() ? null : Double.valueOf(fields[2]));
      quote.setLow(fields[3].isEmpty() ? null : Double.valueOf(fields[3]));
      result.add(quote);
    }
    return result;
  }

  @Test
  @DisplayName("Recorded prices, benchmark and dividends agree with independent reference formulas")
  void recordedInputs() throws Exception {
    var rows = recordedPrices("spi.csv");
    var benchmark = recordedPrices("benchmark.csv");
    var dividends = new ArrayList<DividendAmount>();
    for (var fields : csv("dividends.csv")) {
      assertThat(fields[2]).isEqualTo("CHF");
      dividends.add(new DividendAmount(LocalDate.parse(fields[0]), Double.valueOf(fields[1])));
    }
    double price = rows.getLast().getClose();
    var functions = new IndicatorFunctionSet(new IndicatorSeries(rows, benchmark, dividends, true, true, TODAY, price));
    int n = 14, size = rows.size();
    double[] closes = rows.stream().mapToDouble(Historyquote::getClose).toArray();
    double[] window = Arrays.copyOfRange(closes, size - n, size);
    double mean = Arrays.stream(window).sum() / n;
    double variance = Arrays.stream(window).map(v -> (v - mean) * (v - mean)).sum() / n;
    assertThat(value("SMA(14)", functions)).isCloseTo(mean, within(1e-10));
    assertThat(value("ZSCORE(14)", functions)).isCloseTo((price - mean) / Math.sqrt(variance), within(1e-10));
    assertThat(value("CLOSE(14)", functions)).isEqualTo(closes[size - n - 1]);
    assertThat(value("HIGHEST(14)", functions)).isEqualTo(Arrays.stream(window).max().orElseThrow());
    assertThat(value("LOWEST(14)", functions)).isEqualTo(Arrays.stream(window).min().orElseThrow());
    double ema = closes[0]; // GT starts EMA at the first close, including its warm-up observations.
    double gain = 0, loss = 0, atr = 0;
    for (int i = 1; i < size; i++) {
      double delta = closes[i] - closes[i - 1];
      double g = Math.max(delta, 0), l = Math.max(-delta, 0);
      double tr = Math.max(rows.get(i).getHigh(), closes[i - 1]) - Math.min(rows.get(i).getLow(), closes[i - 1]);
      if (i <= n) {
        gain += g / n;
        loss += l / n;
        atr += tr / n;
      } else {
        gain = (gain * (n - 1) + g) / n;
        loss = (loss * (n - 1) + l) / n;
        atr = (atr * (n - 1) + tr) / n;
      }
      ema += (closes[i] - ema) * 2 / (n + 1);
    }
    assertThat(value("EMA(14)", functions)).isCloseTo(ema, within(1e-10));
    assertThat(value("RSI(14)", functions)).isCloseTo(100 - 100 / (1 + gain / loss), within(1e-9));
    assertThat(value("ATR(14)", functions)).isCloseTo(atr, within(1e-10));
    double[] logs = new double[n];
    double maxDrawdown = 0;
    for (int i = size - n; i < size; i++) {
      logs[i - size + n] = Math.log(closes[i] / closes[i - 1]);
      // Every prior peak against this close: independent of the running-maximum implementation.
      for (int j = size - n - 1; j < i; j++)
        maxDrawdown = Math.max(maxDrawdown, (1 - closes[i] / closes[j]) * 100);
    }
    double logMean = Arrays.stream(logs).average().orElseThrow();
    double logVariance = Arrays.stream(logs).map(v -> Math.pow(v - logMean, 2)).sum() / (n - 1);
    assertThat(value("VOLA(14)", functions)).isCloseTo(Math.sqrt(logVariance * 252) * 100, within(1e-10));
    assertThat(value("MAXDD(14)", functions)).isCloseTo(maxDrawdown, within(1e-10));
    var a = new ArrayList<Double>();
    var b = new ArrayList<Double>();
    for (var own : rows)
      for (var other : benchmark)
        if (own.getDate().equals(other.getDate())) {
          a.add(own.getClose());
          b.add(other.getClose());
        }
    int end = a.size() - 1;
    assertThat(value("RS(14)", functions))
        .isCloseTo((a.get(end) * b.get(end - n) / (a.get(end - n) * b.get(end)) - 1) * 100, within(1e-10));
    double[] ra = new double[n], rb = new double[n];
    for (int i = 0; i < n; i++) {
      int day = end - n + i;
      ra[i] = a.get(day + 1) / a.get(day) - 1;
      rb[i] = b.get(day + 1) / b.get(day) - 1;
    }
    double ma = Arrays.stream(ra).average().orElseThrow(), mb = Arrays.stream(rb).average().orElseThrow();
    double cross = 0, square = 0;
    for (int i = 0; i < n; i++) {
      cross += (ra[i] - ma) * (rb[i] - mb);
      square += Math.pow(rb[i] - mb, 2);
    }
    assertThat(value("BETA(14)", functions)).isCloseTo(cross / square, within(1e-10));
    double yield = dividends.stream().filter(d -> d.exDate().isAfter(TODAY.minusDays(365)))
        .mapToDouble(DividendAmount::amount).sum() / price * 100;
    assertThat(value("DIVYIELD()", functions)).isCloseTo(yield, within(1e-10));
    var totals = new HashMap<Integer, Double>();
    dividends.forEach(d -> totals.merge(d.exDate().getYear(), d.amount(), Double::sum));
    assertThat(value("DIVGROWTH(3)", functions)).isCloseTo((Math.cbrt(totals.get(2025) / totals.get(2022)) - 1) * 100,
        within(1e-10));
    int streak = 0;
    for (int year = 2025; year >= 2006 && totals.getOrDefault(year, 0.0) > 0
        && totals.getOrDefault(year, 0.0) >= totals.getOrDefault(year - 1, 0.0); year--)
      streak++;
    assertThat(value("DIVYEARS()", functions)).isEqualTo(streak);
  }

}
