package grafioschtrader.reports.dataverification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.HistoryquotePeriod;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.Tenant;
import grafioschtrader.reports.PerformanceReport;
import grafioschtrader.reports.SecurityPeriodPerformanceReport;
import grafioschtrader.reports.SecurityPositionReplay;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.performance.FirstAndMissingTradingDays;
import grafioschtrader.reportviews.performance.IPeriodHolding;
import grafioschtrader.reportviews.performance.SecurityPeriodPerformance;
import grafioschtrader.reportviews.performance.SecurityPeriodPerformanceRow;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.HistoryquotePeriodJpaRepository;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;
import grafioschtrader.service.CostBasisMethodService;
import grafioschtrader.test.start.GTforTest;

/**
 * Proves the period performance per security against two independent sources on a real database, for every completed
 * calendar year and the whole history of one client and of each of its portfolios:
 *
 * <ul>
 * <li>the sum of the end values against {@code securitiesMC + marginCloseGainMC} of the period performance on the end
 * date, which shows the daily valuation of the holding rows was carried over unchanged;</li>
 * <li>the gain in security currency of every security against the change of {@code gainLossSecurity} of the security
 * account calculation between the two dates, which shows the flows of the replay are complete. The reference values
 * open positions with the close of the date and without the disposal cost estimate. Margin instruments and securities
 * valued through price periods are left out, because that calculation rounds margin lots and reads price periods only
 * for a security without any close.</li>
 * </ul>
 *
 * <p>
 * The test does not assert; it prints every deviation and the duration of each complete report call, to be read. It is
 * a maintainer utility, skipped unless the tenant is named:
 * </p>
 *
 * <pre>
 * cd backend
 * mvn -pl grafioschtrader-server test -Dtest=SecurityPeriodPerformanceVerificationTest \
 *   -Dgt.security.period.performance.tenant=7
 * </pre>
 *
 * <p>
 * <b>{@code @ActiveProfiles("prod")} is not optional.</b> Without an explicit profile the context boots the test
 * bootstrap and {@code V1__schema.sql} drops every table of the target database. Nothing here opens a write path.
 * </p>
 */
@SpringBootTest(classes = GTforTest.class)
@ActiveProfiles("prod")
@Transactional
class SecurityPeriodPerformanceVerificationTest {

  private static final String TENANT_PROPERTY = "gt.security.period.performance.tenant";
  /** Below this many security currency units a deviation of the gain is rounding. */
  private static final double GAIN_TOLERANCE = 0.05;
  /** The period performance rounds its two sums to two digits, this report rounds once. */
  private static final double VALUE_TOLERANCE = 0.02;

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

  @Autowired
  private PerformanceReport performanceReport;

  @Autowired
  private SecurityPeriodPerformanceReport securityPeriodPerformanceReport;

  @Autowired
  private SecurityPositionReplay securityPositionReplay;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private HoldSecurityaccountSecurityJpaRepository holdSecurityaccountSecurityJpaRepository;

  @Autowired
  private HistoryquoteJpaRepository historyquoteJpaRepository;

  @Autowired
  private HistoryquotePeriodJpaRepository historyquotePeriodJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  private TradingDaysPlusJpaRepository tradingDaysPlusJpaRepository;

  @Autowired
  private CostBasisMethodService costBasisMethodService;

  private int periods;
  private int comparedRows;
  private int gainDeviations;
  private int valueDeviations;
  private long slowestMillis;

  @Test
  void verifySecurityPeriodPerformance() throws Exception {
    String tenantProperty = System.getProperty(TENANT_PROPERTY);
    if (tenantProperty == null) {
      return;
    }
    Tenant tenant = tenantJpaRepository.findById(Integer.valueOf(tenantProperty)).orElseThrow();
    LocalDate today = LocalDate.now();
    Set<Integer> withPricePeriods = new HashSet<>();
    historyquotePeriodJpaRepository.findAll().stream().map(HistoryquotePeriod::getIdSecuritycurrency)
        .forEach(withPricePeriods::add);
    verifyScope(tenant, null, today, withPricePeriods);
    for (Portfolio portfolio : tenant.getPortfolioList()) {
      verifyScope(tenant, portfolio, today, withPricePeriods);
    }
    System.out.printf("%nPeriods: %d, rows compared: %d, gain deviations: %d, value deviations: %d, slowest: %d ms%n",
        periods, comparedRows, gainDeviations, valueDeviations, slowestMillis);
  }

  private void verifyScope(Tenant tenant, Portfolio portfolio, LocalDate today, Set<Integer> withPricePeriods)
      throws Exception {
    Integer idTenant = tenant.getIdTenant();
    FirstAndMissingTradingDays days = portfolio == null
        ? performanceReport.getFirstAndMissingTradingDaysByTenant(idTenant, today)
        : performanceReport.getFirstAndMissingTradingDaysByPortfolio(idTenant, portfolio.getIdPortfolio(), today);
    if (days.latestTradingDay == null || days.secondLatestTradingDay == null) {
      return;
    }
    TreeSet<LocalDate> valid = validDays(days);
    if (valid.size() < 2) {
      return;
    }
    for (LocalDate[] period : periods(valid, days.secondLatestTradingDay)) {
      verifyPeriod(tenant, portfolio, today, period[0], period[1], withPricePeriods);
    }
  }

  /** Valuation days of the scope: trading days without holidays and days with missing quotes. */
  private TreeSet<LocalDate> validDays(FirstAndMissingTradingDays days) {
    TreeSet<LocalDate> valid = new TreeSet<>();
    tradingDaysPlusJpaRepository.findByTradingDateBetweenOrderByTradingDate(days.firstEverTradingDay,
        days.latestTradingDay).forEach(t -> {
          LocalDate d = t.getTradingDate();
          if (!days.allHolydays.contains(d) && !days.missingQuoteDays.contains(d)) {
            valid.add(d);
          }
        });
    return valid;
  }

  /**
   * Every completed calendar year and the whole history, all ending before the newest valuation day, whose valuation
   * may use an intraday price.
   */
  private static List<LocalDate[]> periods(TreeSet<LocalDate> valid, LocalDate lastEnd) {
    List<LocalDate[]> periods = new ArrayList<>();
    for (int year = valid.first().getYear(); year < lastEnd.getYear(); year++) {
      LocalDate to = valid.floor(LocalDate.of(year, 12, 31));
      LocalDate from = valid.floor(LocalDate.of(year - 1, 12, 31));
      from = from == null ? valid.first() : from;
      if (to != null && from.isBefore(to)) {
        periods.add(new LocalDate[] { from, to });
      }
    }
    LocalDate end = valid.floor(lastEnd);
    if (end != null && valid.first().isBefore(end)) {
      periods.add(new LocalDate[] { valid.first(), end });
    }
    return periods;
  }

  private void verifyPeriod(Tenant tenant, Portfolio portfolio, LocalDate today, LocalDate dateFrom, LocalDate dateTo,
      Set<Integer> withPricePeriods) throws Exception {
    String scope = portfolio == null ? "tenant " + tenant.getIdTenant() : "portfolio " + portfolio.getIdPortfolio();
    long start = System.nanoTime();
    SecurityPeriodPerformance result = portfolio == null
        ? securityPeriodPerformanceReport.getByTenant(tenant.getIdTenant(), "de", today, dateFrom, dateTo)
        : securityPeriodPerformanceReport.getByPortfolio(tenant.getIdTenant(), "de", today,
            portfolio.getIdPortfolio(), dateFrom, dateTo);
    long millis = (System.nanoTime() - start) / 1_000_000;
    slowestMillis = Math.max(slowestMillis, millis);
    periods++;
    System.out.printf("%s %s..%s: %d rows, %d omitted, %d ms%n", scope, dateFrom, dateTo, result.rows().size(),
        result.omittedRows(), millis);
    verifyValueSum(tenant, portfolio, result, scope);
    verifyGains(tenant, portfolio, today, result, withPricePeriods, scope);
    printImplausibleReturns(result, scope);
  }

  /** Lists returns outside -100 % to +300 % with their days held, to be judged by reading. */
  private static void printImplausibleReturns(SecurityPeriodPerformance result, String scope) {
    for (SecurityPeriodPerformanceRow row : result.rows()) {
      Double twr = row.twrPercent();
      if (twr != null && (twr < -100 || twr > 300)) {
        System.out.printf("  TWR %s %s..%s %s (%d): %.2f %% over %d days, gain %.2f%n", scope, result.dateFrom(),
            result.dateTo(), row.name(), row.idSecuritycurrency(), twr, row.holdingDays(), row.gainPeriodMC());
      }
    }
  }

  private void verifyValueSum(Tenant tenant, Portfolio portfolio, SecurityPeriodPerformance result, String scope) {
    List<IPeriodHolding> holdings = portfolio == null
        ? holdSecurityaccountSecurityJpaRepository.getPeriodHoldingsByTenant(tenant.getIdTenant(), result.dateTo(),
            result.dateTo())
        : holdSecurityaccountSecurityJpaRepository.getPeriodHoldingsByPortfolio(portfolio.getIdPortfolio(),
            result.dateTo(), result.dateTo());
    if (holdings.isEmpty()) {
      return;
    }
    double expected = holdings.getFirst().getSecuritiesMC() + holdings.getFirst().getMarginCloseGainMC();
    double actual = result.totals().valueEndMC();
    if (Math.abs(expected - actual) > VALUE_TOLERANCE) {
      valueDeviations++;
      System.out.printf("  VALUE %s %s: period performance %.2f, sum of rows %.2f%n", scope, result.dateTo(), expected,
          actual);
    }
  }

  private void verifyGains(Tenant tenant, Portfolio portfolio, LocalDate today, SecurityPeriodPerformance result,
      Set<Integer> withPricePeriods, String scope) {
    Map<Integer, Double> gainFrom = referenceGains(tenant, portfolio, today, result.dateFrom());
    Map<Integer, Double> gainTo = referenceGains(tenant, portfolio, today, result.dateTo());
    for (SecurityPeriodPerformanceRow row : result.rows()) {
      if (row.marginInstrument() || row.gainPeriod() == null
          || withPricePeriods.contains(row.idSecuritycurrency())
          || !gainTo.containsKey(row.idSecuritycurrency())) {
        continue;
      }
      comparedRows++;
      double expected = gainTo.get(row.idSecuritycurrency())
          - gainFrom.getOrDefault(row.idSecuritycurrency(), 0.0);
      if (Math.abs(expected - row.gainPeriod()) > GAIN_TOLERANCE) {
        gainDeviations++;
        System.out.printf("  GAIN %s %s..%s %s (%d): reference %.2f, report %.2f, remark %s%n", scope,
            result.dateFrom(), result.dateTo(), row.name(), row.idSecuritycurrency(), expected, row.gainPeriod(),
            row.remark());
      }
    }
  }

  /**
   * {@code gainLossSecurity} per security at the close of a date, as the security account calculation forms it: the
   * transactions booked up to that date, open positions valued with the close of that date, no disposal estimate. A
   * security whose price is missing is left out.
   */
  private Map<Integer, Double> referenceGains(Tenant tenant, Portfolio portfolio, LocalDate today, LocalDate date) {
    String mainCurrency = portfolio == null ? tenant.getCurrency() : portfolio.getCurrency();
    List<Securityaccount> accounts = portfolio == null
        ? tenant.getPortfolioList().stream().flatMap(p -> p.getSecurityaccountList().stream()).toList()
        : portfolio.getSecurityaccountList();
    List<Object[]> rates = portfolio == null
        ? historyquoteJpaRepository.getHistoryquotesForAllForeignTransactionsByIdTenant(tenant.getIdTenant())
        : historyquoteJpaRepository.getHistoryquotesForAllForeignTransactionsByIdPortfolio(portfolio.getIdPortfolio());
    DateTransactionCurrencypairMap currencyMap = new DateTransactionCurrencypairMap(mainCurrency, date, rates,
        currencypairJpaRepository.getAllCurrencypairsByTenantInPortfolioAndAccounts(tenant.getIdTenant()),
        tradingDaysPlusJpaRepository.hasTradingDayBetweenUntilYesterday(date, today), true, today);
    Map<Security, SecurityPositionSummary> summaries = new HashMap<>();
    securityPositionReplay.replay(accounts, summaries, tenant.isExcludeDivTax(), currencyMap,
        costBasisMethodService.forTenant(tenant.getIdTenant()), t -> !t.getTransactionDate().isAfter(date), null,
        null);
    securityJpaRepository.processOpenPositionsWithActualPrice(date, summaries);
    Map<Integer, Double> gains = new HashMap<>();
    summaries.forEach((security, summary) -> {
      if (!summary.priceMissing) {
        gains.put(security.getIdSecuritycurrency(), summary.gainLossSecurity);
      }
    });
    return gains;
  }
}
