package grafioschtrader.reports;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.reportviews.performance.BenchmarkDescriptor;
import grafioschtrader.reportviews.performance.BenchmarkNotice;
import grafioschtrader.reportviews.performance.BenchmarkSeries;
import grafioschtrader.reportviews.performance.BenchmarkSeries.Quote;
import grafioschtrader.repository.DividendJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.reports.ClosingRateLoader.ConversionPair;
import grafioschtrader.service.PerformanceBenchmarkService.ResolvedBenchmark;
import grafioschtrader.types.BenchmarkNoticeCode;
import grafioschtrader.types.BenchmarkReturnBasis;
import grafioschtrader.types.HistoryquoteCreateType;

//@formatter:off
/**
 * Loads the quotes of a resolved benchmark and of its conversion rate for the valuation dates of a report and aligns
 * them with {@link BenchmarkSeries}.
 *
 * <ul>
 * <li>Only reliable quotes count: created by a connector, imported, calculated or entered by a user. Filled rows
 * (non-trading days, linear fills, gaps filled by a connector) are never an observation.</li>
 * <li>Each leg starts with the latest reliable quote on or before the first valuation date, so no fixed lookback can
 * hide a quote; {@link BenchmarkSeries} decides whether it is fresh enough.</li>
 * <li>A dividend-capable security has its recorded gross distributions reinvested. Every other instrument, among
 * them indices and derived instruments, is compared as supplied.</li>
 * <li>The rate comes from the direct pair when it has reliable rates, otherwise from the inverse pair.</li>
 * </ul>
 */
//@formatter:on
@Component
public class BenchmarkSeriesLoader {

  /** Byte values of the quotes that count as an observation. */
  static final List<Byte> RELIABLE_CREATE_TYPES = List.of(HistoryquoteCreateType.CONNECTOR_CREATED.getValue(),
      HistoryquoteCreateType.MANUAL_IMPORTED.getValue(), HistoryquoteCreateType.CALCULATED.getValue(),
      HistoryquoteCreateType.ADD_MODIFIED_USER.getValue());

  private final HistoryquoteJpaRepository historyquoteJpaRepository;
  private final DividendJpaRepository dividendJpaRepository;
  private final ClosingRateLoader closingRateLoader;

  public BenchmarkSeriesLoader(HistoryquoteJpaRepository historyquoteJpaRepository,
      DividendJpaRepository dividendJpaRepository, ClosingRateLoader closingRateLoader) {
    this.historyquoteJpaRepository = historyquoteJpaRepository;
    this.dividendJpaRepository = dividendJpaRepository;
    this.closingRateLoader = closingRateLoader;
  }

  /**
   * The descriptor of a benchmark and its series aligned on the valuation dates.
   *
   * @param descriptor identity, conversion, return basis and the notices found while loading
   * @param aligned    the aligned series, null when no comparison can be formed at all
   */
  public record LoadedBenchmark(BenchmarkDescriptor descriptor, BenchmarkSeries.Result aligned) {

    /** Whether a comparison span exists. */
    public boolean hasSpan() {
      return aligned != null && aligned.hasSpan();
    }

    /** The notices of loading followed by those of the alignment. */
    public List<BenchmarkNotice> allNotices() {
      List<BenchmarkNotice> notices = new ArrayList<>(descriptor.notices());
      if (aligned != null) {
        notices.addAll(aligned.notices());
      }
      return notices;
    }
  }

  /**
   * Loads and aligns a benchmark.
   *
   * @param resolved     the resolved benchmark
   * @param mainCurrency currency of the report
   * @param dates        valuation dates of the report in ascending order, the first one is the excluded base
   * @return the descriptor and the aligned series
   */
  public LoadedBenchmark load(ResolvedBenchmark resolved, String mainCurrency, List<LocalDate> dates) {
    String currency = resolved.currency();
    List<BenchmarkNotice> notices = new ArrayList<>();
    Security security = resolved.instrument() instanceof Security s ? s : null;
    boolean adjust = security != null && security.getAssetClass() != null && security.isDividendCapableInstrument();
    BenchmarkReturnBasis basis = adjust ? BenchmarkReturnBasis.DIVIDENDS_REINVESTED : BenchmarkReturnBasis.AS_SUPPLIED;

    if (security != null && security.getStockexchange().isNoMarketValue()) {
      notices.add(BenchmarkNotice.of(BenchmarkNoticeCode.BENCHMARK_PERIOD_PRICES));
      return new LoadedBenchmark(descriptor(resolved, mainCurrency, null, basis, notices), null);
    }
    ConversionPair pair = null;
    List<Quote> rates = null;
    if (!currency.equals(mainCurrency)) {
      List<ConversionPair> candidates = closingRateLoader.conversionPairs(currency, mainCurrency);
      if (candidates.isEmpty()) {
        notices.add(BenchmarkNotice.of(BenchmarkNoticeCode.BENCHMARK_NO_FX_PAIR, "from", currency, "to",
            mainCurrency));
        return new LoadedBenchmark(descriptor(resolved, mainCurrency, null, basis, notices), null);
      }
      pair = candidates.getFirst();
      rates = rateQuotes(pair, dates);
      if (rates.isEmpty() && candidates.size() > 1) {
        pair = candidates.get(1);
        rates = rateQuotes(pair, dates);
      }
    }
    List<Historyquote> history = reliableQuotes(resolved.instrument().getIdSecuritycurrency(), dates);
    List<Quote> closes = adjust ? adjustedCloses(security, history, dates, notices) : toQuotes(history);
    BenchmarkSeries.Result aligned = BenchmarkSeries
        .align(new BenchmarkSeries.Input(dates, closes, rates, currency, mainCurrency));
    return new LoadedBenchmark(descriptor(resolved, mainCurrency, pair, basis, notices), aligned);
  }

  /**
   * Reinvests the recorded gross distributions of a dividend-capable security. The horizon is the latest reliable
   * quote, so a later ex-date from a dividend calendar does not shift the series.
   */
  private List<Quote> adjustedCloses(Security security, List<Historyquote> history, List<LocalDate> dates,
      List<BenchmarkNotice> notices) {
    if (history.isEmpty()) {
      return List.of();
    }
    LocalDate horizon = history.getLast().getDate();
    TotalReturnAdjuster adjuster = TotalReturnAdjuster.load(dividendJpaRepository, List.of(security), horizon);
    if (adjuster.isMissingDividends(security.getIdSecuritycurrency(), dates.getFirst(), dates.getLast())) {
      notices.add(BenchmarkNotice.of(BenchmarkNoticeCode.BENCHMARK_MISSING_DIVIDENDS));
    }
    var factors = adjuster.getFactors(security.getIdSecuritycurrency());
    return history.stream().map(h -> new Quote(h.getDate(), h.getClose() * factors.factorForDate(h.getDate())))
        .toList();
  }

  private List<Quote> rateQuotes(ConversionPair pair, List<LocalDate> dates) {
    return reliableQuotes(pair.pair().getIdSecuritycurrency(), dates).stream()
        .map(h -> new Quote(h.getDate(), pair.inverse() ? 1 / h.getClose() : h.getClose())).toList();
  }

  /** The latest reliable quote on or before the first date, followed by the reliable quotes up to the last date. */
  private List<Historyquote> reliableQuotes(Integer idSecuritycurrency, List<LocalDate> dates) {
    LocalDate first = dates.getFirst();
    List<Historyquote> quotes = new ArrayList<>();
    var seed = historyquoteJpaRepository
        .findFirstByIdSecuritycurrencyAndCreateTypeInAndDateLessThanEqualOrderByDateDesc(idSecuritycurrency,
            RELIABLE_CREATE_TYPES, first);
    seed.ifPresent(quotes::add);
    LocalDate from = seed.map(h -> h.getDate().plusDays(1)).orElse(first);
    historyquoteJpaRepository.findByIdSecuritycurrencyAndDateBetweenOrderByDate(idSecuritycurrency, from,
        dates.getLast()).stream().filter(h -> RELIABLE_CREATE_TYPES.contains(h.getCreateType().getValue()))
        .forEach(quotes::add);
    return quotes;
  }

  private static List<Quote> toQuotes(List<Historyquote> history) {
    return history.stream().map(h -> new Quote(h.getDate(), h.getClose())).toList();
  }

  private static BenchmarkDescriptor descriptor(ResolvedBenchmark resolved, String mainCurrency, ConversionPair pair,
      BenchmarkReturnBasis basis, List<BenchmarkNotice> notices) {
    return new BenchmarkDescriptor(resolved.instrument().getIdSecuritycurrency(), resolved.instrument().getName(),
        resolved.currency(), resolved.source(), mainCurrency, pair == null ? null : pair.name(),
        pair != null && pair.inverse(), basis, List.copyOf(notices));
  }
}
