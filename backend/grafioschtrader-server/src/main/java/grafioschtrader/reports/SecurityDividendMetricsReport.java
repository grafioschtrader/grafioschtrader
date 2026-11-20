package grafioschtrader.reports;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafioschtrader.entities.Security;
import grafioschtrader.reports.DividendMetricsCalculator.ClosePoint;
import grafioschtrader.reports.DividendMetricsCalculator.DividendPoint;
import grafioschtrader.reportviews.securitydividends.SecurityDividendChart;
import grafioschtrader.reportviews.securitydividends.SecurityDividendChart.Series;
import grafioschtrader.reportviews.securitydividends.SecurityDividendMetrics;
import grafioschtrader.reportviews.securitydividends.SecurityDividendMetrics.SplitMark;
import grafioschtrader.repository.DividendJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.SecurityFundamentalJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.types.DistributionFrequency;
import grafioschtrader.types.FundamentalMetric;
import grafioschtrader.types.HistoryquoteCreateType;

/**
 * Collects the input of the dividend key figures of one security and hands it to {@link DividendMetricsCalculator}.
 * The figures describe the instrument as the shared dividend table records it; the distributions a tenant received are
 * the subject of {@link SecurityDividendsReport}.
 */
@Component
public class SecurityDividendMetricsReport {

  /** Lower bound of the ex-date range, older than any dividend a connector delivers. */
  private static final LocalDate ALL_FROM = LocalDate.of(1900, 1, 1);
  /** Upper bound of the ex-date range, which also takes in already announced dividends. */
  private static final LocalDate ALL_TO = LocalDate.of(2999, 12, 31);
  /** Most securities one dividend chart compares; further ids of a request are ignored. */
  public static final int MAX_CHART_SECURITIES = 10;
  /** Distribution frequencies whose value is the number of payments per year. */
  private static final Set<DistributionFrequency> REGULAR = Set.of(DistributionFrequency.DF_YEAR,
      DistributionFrequency.DF_SEMI_ANNUAL, DistributionFrequency.DF_QUARTERLY, DistributionFrequency.DF_MONTHLY);

  @Autowired
  private SecurityJpaRepository securityJpaRepository;
  @Autowired
  private DividendJpaRepository dividendJpaRepository;
  @Autowired
  private HistoryquoteJpaRepository historyquoteJpaRepository;
  @Autowired
  private SecuritysplitJpaRepository securitysplitJpaRepository;
  @Autowired
  private SecurityFundamentalJpaRepository securityFundamentalJpaRepository;

  /**
   * Computes the dividend key figures of a security.
   *
   * @param idSecuritycurrency the security; a currency pair or an unknown id yields figures without any dividend
   * @param today              the calendar day of the client
   * @return the key figures in the currency of the security
   */
  public SecurityDividendMetrics getMetrics(Integer idSecuritycurrency, LocalDate today) {
    Security security = securityJpaRepository.findById(idSecuritycurrency).orElse(null);
    return security == null ? SecurityDividendMetrics.empty(null, null) : getMetrics(security, today);
  }

  /**
   * Builds the series of the dividend chart. Duplicate ids are removed, ids of currency pairs or unknown instruments
   * are skipped, and at most {@link #MAX_CHART_SECURITIES} securities are taken.
   *
   * @param idsSecuritycurrency the securities in the order of the chart
   * @param today               the calendar day of the client
   * @return one series per security and the common base year of the index
   */
  public SecurityDividendChart getChart(List<Integer> idsSecuritycurrency, LocalDate today) {
    List<Security> securities = new ArrayList<>();
    for (Integer id : idsSecuritycurrency.stream().distinct().limit(MAX_CHART_SECURITIES).toList()) {
      securityJpaRepository.findById(id).ifPresent(securities::add);
    }
    List<SecurityDividendMetrics> metrics = securities.stream().map(s -> getMetrics(s, today)).toList();
    int lastCompletedYear = today.getYear() - 1;
    Integer baseYear = DividendMetricsCalculator
        .indexBaseYear(metrics.stream().map(SecurityDividendMetrics::years).toList(), lastCompletedYear);
    List<Series> series = new ArrayList<>();
    for (int i = 0; i < securities.size(); i++) {
      Security security = securities.get(i);
      SecurityDividendMetrics m = metrics.get(i);
      series.add(new Series(security.getIdSecuritycurrency(), security.getName(), security.getCurrency(), m.years(),
          m.yieldHistory(), m.splits(), DividendMetricsCalculator.index(m.years(), baseYear, lastCompletedYear)));
    }
    return new SecurityDividendChart(baseYear, series);
  }

  private SecurityDividendMetrics getMetrics(Security security, LocalDate today) {
    Integer idSecuritycurrency = security.getIdSecuritycurrency();
    List<DividendPoint> dividends = dividendJpaRepository
        .findByIdsAndExDateRangeWithRate(List.of(idSecuritycurrency), ALL_FROM, ALL_TO).stream()
        .map(row -> new DividendPoint(row.getExDate(),
            row.getExchangeRate() == null ? null : row.getAmountAdjusted() * row.getExchangeRate()))
        .toList();
    if (dividends.isEmpty()) {
      return SecurityDividendMetrics.empty(security.getCurrency(), security.getSLast());
    }
    List<ClosePoint> closes = loadCloses(security);
    Double price = security.getSLast() != null ? security.getSLast()
        : closes.isEmpty() ? null : closes.getLast().close();
    DistributionFrequency frequency = security.getDistributionFrequency();
    Integer paymentsPerYear = REGULAR.contains(frequency) ? (int) frequency.getValue() : null;
    List<SplitMark> splits = securitysplitJpaRepository.findByIdSecuritycurrencyOrderBySplitDateAsc(idSecuritycurrency)
        .stream().map(s -> new SplitMark(s.getSplitDate(), s.getFromFactor(), s.getToFactor())).toList();
    return DividendMetricsCalculator.calculate(security.getCurrency(), dividends, price, paymentsPerYear, closes,
        loadEpsTTM(idSecuritycurrency, today), splits, today);
  }

  /** Closes without the filled non-trading days; none for an instrument without market value. */
  private List<ClosePoint> loadCloses(Security security) {
    if (security.getStockexchange() != null && security.getStockexchange().isNoMarketValue()) {
      return List.of();
    }
    return historyquoteJpaRepository
        .getByIdSecuritycurrencyAndCreateTypeNotOrderByDate(security.getIdSecuritycurrency(),
            HistoryquoteCreateType.FILLED_NON_TRADE_DAY.getValue())
        .stream().filter(c -> c.getClose() != null).map(c -> new ClosePoint(c.getDate(), c.getClose())).toList();
  }

  /** Trailing earnings per share of the fundamental selection, converted into the currency of the security. */
  private Double loadEpsTTM(Integer idSecuritycurrency, LocalDate today) {
    return securityFundamentalJpaRepository.findSelectedByIds(List.of(idSecuritycurrency), today).stream()
        .filter(row -> row.getMetric() == FundamentalMetric.EPS_TTM.getValue() && row.getValue() != null
            && row.getExchangeRate() != null)
        .map(row -> row.getValue() * row.getExchangeRate()).findFirst().orElse(null);
  }
}
