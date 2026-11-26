package grafioschtrader.reportviews.performance;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import grafioschtrader.types.BenchmarkNoticeCode;

//@formatter:off
/**
 * Aligns the quotes of a benchmark instrument and of its conversion rate on the valuation dates of a report and
 * derives the comparison span. A pure function of its input, fixed by hand-calculated tests.
 *
 * <ul>
 * <li>Only reliable quotes are given to this class; filled rows are removed by the caller.</li>
 * <li>On each valuation date a leg takes the latest quote dated on or before it, provided that quote is at most
 * {@link #MAX_CARRY_DAYS} calendar days old; otherwise the leg is uncovered on that date. An identity conversion is
 * always covered.</li>
 * <li>The level in main currency is {@code b_i = close_i * rate_i}. A reliable value that is zero, negative or not
 * finite is invalid data: no comparison is formed and a notice names its date and leg.</li>
 * <li>The span runs from the first date covered by both legs to the last date of the uninterrupted run of covered
 * dates after it. Nothing bridges a later gap; levels outside the span are null.</li>
 * <li>A carried close with a moving rate still changes the level. That is a real currency return in the main currency,
 * but the instrument is not observed on that date.</li>
 * </ul>
 */
//@formatter:on
public final class BenchmarkSeries {

  /** Longest period in calendar days a reliable quote is carried forward to a valuation date. */
  public static final int MAX_CARRY_DAYS = 5;

  private BenchmarkSeries() {
  }

  /**
   * A reliable quote of one leg.
   *
   * @param date  date of the quote
   * @param value close of the instrument, already adjusted for reinvested distributions, or the conversion rate
   */
  public record Quote(LocalDate date, double value) {
  }

  /**
   * Everything the alignment needs.
   *
   * @param dates        valuation dates of the report in ascending order, the first one is the excluded base
   * @param instrument   reliable quotes of the instrument, including the latest one on or before the first date
   * @param rate         reliable rates from the instrument currency into the main currency, null for identity
   * @param fromCurrency instrument currency, a parameter of the conversion notices
   * @param toCurrency   main currency, a parameter of the conversion notices
   */
  public record Input(List<LocalDate> dates, List<Quote> instrument, List<Quote> rate, String fromCurrency,
      String toCurrency) {
  }

  /**
   * Levels and coverage of the benchmark on the valuation dates.
   *
   * @param levels             level in main currency per valuation date, null outside the span
   * @param instrumentObserved whether the instrument has a quote dated exactly on that valuation date
   * @param rateObserved       whether the rate has a quote dated exactly on that valuation date, always true for an
   *                           identity conversion
   * @param k                  index of the first date of the span, -1 without a span
   * @param e                  index of the last date of the span, -1 without a span
   * @param lastInstrumentDate date of the latest reliable instrument quote up to the last valuation date
   * @param lastRateDate       date of the latest reliable rate up to the last valuation date, null for identity
   * @param notices            conditions found during the alignment
   */
  public record Result(Double[] levels, boolean[] instrumentObserved, boolean[] rateObserved, int k, int e,
      LocalDate lastInstrumentDate, LocalDate lastRateDate, List<BenchmarkNotice> notices) {

    /** Whether a comparison span exists at all. */
    public boolean hasSpan() {
      return k >= 0;
    }
  }

  /**
   * Aligns both legs on the valuation dates.
   *
   * @param input dates and quotes
   * @return the levels, the span and the notices
   */
  public static Result align(Input input) {
    List<LocalDate> dates = input.dates();
    int n = dates.size();
    List<Quote> instrument = sorted(input.instrument());
    List<Quote> rate = input.rate() == null ? null : sorted(input.rate());
    List<BenchmarkNotice> notices = new ArrayList<>();

    Quote invalid = firstInvalid(instrument);
    String invalidLeg = BenchmarkNotice.LEG_INSTRUMENT;
    if (invalid == null && rate != null) {
      invalid = firstInvalid(rate);
      invalidLeg = BenchmarkNotice.LEG_RATE;
    }
    LocalDate lastDate = n == 0 ? null : dates.get(n - 1);
    LocalDate lastInstrumentDate = lastQuoteDate(instrument, lastDate);
    LocalDate lastRateDate = rate == null ? null : lastQuoteDate(rate, lastDate);
    if (invalid != null) {
      notices.add(BenchmarkNotice.of(BenchmarkNoticeCode.BENCHMARK_INVALID_PRICE, "date", invalid.date(), "leg",
          invalidLeg));
      return empty(n, lastInstrumentDate, lastRateDate, notices);
    }

    Leg instrumentLeg = Leg.of(dates, instrument);
    Leg rateLeg = rate == null ? Leg.identity(n) : Leg.of(dates, rate);
    int k = -1;
    for (int i = 0; i < n && k < 0; i++) {
      if (instrumentLeg.covered[i] && rateLeg.covered[i]) {
        k = i;
      }
    }
    if (k < 0) {
      notices.add(noSpanNotice(instrumentLeg, input));
      return empty(n, lastInstrumentDate, lastRateDate, notices);
    }
    int e = k;
    while (e + 1 < n && instrumentLeg.covered[e + 1] && rateLeg.covered[e + 1]) {
      e++;
    }
    Double[] levels = new Double[n];
    for (int i = k; i <= e; i++) {
      levels[i] = instrumentLeg.values[i] * rateLeg.values[i];
    }
    if (k > 0) {
      notices.add(BenchmarkNotice.of(BenchmarkNoticeCode.BENCHMARK_START_LATER, "startDate", dates.get(k)));
    }
    if (e < n - 1) {
      notices.add(BenchmarkNotice.of(BenchmarkNoticeCode.BENCHMARK_STALE, "endDate", dates.get(e),
          "lastInstrumentDate", lastInstrumentDate, "lastRateDate", lastRateDate));
    }
    return new Result(levels, instrumentLeg.observed, rateLeg.observed, k, e, lastInstrumentDate, lastRateDate,
        notices);
  }

  private static BenchmarkNotice noSpanNotice(Leg instrumentLeg, Input input) {
    for (boolean covered : instrumentLeg.covered) {
      if (covered) {
        return BenchmarkNotice.of(BenchmarkNoticeCode.BENCHMARK_NO_FX_HISTORY, "from", input.fromCurrency(), "to",
            input.toCurrency());
      }
    }
    return BenchmarkNotice.of(BenchmarkNoticeCode.BENCHMARK_NO_QUOTES);
  }

  private static Result empty(int n, LocalDate lastInstrumentDate, LocalDate lastRateDate,
      List<BenchmarkNotice> notices) {
    return new Result(new Double[n], new boolean[n], new boolean[n], -1, -1, lastInstrumentDate, lastRateDate,
        notices);
  }

  private static List<Quote> sorted(List<Quote> quotes) {
    List<Quote> copy = new ArrayList<>(quotes);
    copy.sort(Comparator.comparing(Quote::date));
    return copy;
  }

  private static Quote firstInvalid(List<Quote> quotes) {
    return quotes.stream().filter(q -> !Double.isFinite(q.value()) || q.value() <= 0).findFirst().orElse(null);
  }

  private static LocalDate lastQuoteDate(List<Quote> quotes, LocalDate lastDate) {
    LocalDate last = null;
    for (Quote quote : quotes) {
      if (lastDate == null || !quote.date().isAfter(lastDate)) {
        last = quote.date();
      }
    }
    return last;
  }

  /** Value, coverage and observation of one leg on every valuation date. */
  private record Leg(double[] values, boolean[] covered, boolean[] observed) {

    static Leg identity(int n) {
      double[] values = new double[n];
      boolean[] all = new boolean[n];
      java.util.Arrays.fill(values, 1.0);
      java.util.Arrays.fill(all, true);
      return new Leg(values, all, all.clone());
    }

    static Leg of(List<LocalDate> dates, List<Quote> quotes) {
      int n = dates.size();
      double[] values = new double[n];
      boolean[] covered = new boolean[n];
      boolean[] observed = new boolean[n];
      int q = -1;
      for (int i = 0; i < n; i++) {
        LocalDate date = dates.get(i);
        while (q + 1 < quotes.size() && !quotes.get(q + 1).date().isAfter(date)) {
          q++;
        }
        if (q >= 0) {
          Quote quote = quotes.get(q);
          if (ChronoUnit.DAYS.between(quote.date(), date) <= MAX_CARRY_DAYS) {
            values[i] = quote.value();
            covered[i] = true;
            observed[i] = quote.date().isEqual(date);
          }
        }
      }
      return new Leg(values, covered, observed);
    }
  }
}
