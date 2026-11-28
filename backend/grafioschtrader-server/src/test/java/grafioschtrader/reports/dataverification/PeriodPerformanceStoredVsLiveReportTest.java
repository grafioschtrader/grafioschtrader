package grafioschtrader.reports.dataverification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.exceptions.DataViolationException;
import grafioschtrader.entities.HoldDailyTotalState;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Tenant;
import grafioschtrader.reports.PerformanceReport;
import grafioschtrader.reports.PeriodHoldingsLoader;
import grafioschtrader.reportviews.performance.FirstAndMissingTradingDays;
import grafioschtrader.reportviews.performance.IPeriodHolding;
import grafioschtrader.reportviews.performance.PerformancePeriod;
import grafioschtrader.reportviews.performance.WeekYear;
import grafioschtrader.repository.HoldDailyTotalJpaRepository;
import grafioschtrader.repository.HoldDailyTotalStateJpaRepository;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;
import grafioschtrader.test.start.GTforTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

//@formatter:off
/**
 * Proves on a real database that the period performance is the same whether its daily rows come from
 * {@code hold_daily_total} through {@link PeriodHoldingsLoader} or are all computed by the period holdings query. For
 * one client and each of its portfolios it computes the complete {@link PerformancePeriod} both ways and compares
 * every field of the serialized result: the first-day, last-day and difference totals, the windows and steps, the
 * return, risk and cost figures and the benchmark comparison. Ranges:
 * <ul>
 * <li>the whole history, split into years;</li>
 * <li>the last completed calendar year, split into years and into weeks;</li>
 * <li>a range of about 400 days ending on the newest valuation day, which crosses the marker
 * {@code recalc_from_date} of the client, split into years.</li>
 * </ul>
 *
 * <p>
 * It prints the duration of both ways for every call, which is the measurement before and after the change, lists the
 * first differing fields, and fails when any field differs. It is a maintainer utility, skipped unless the client is
 * named:
 * </p>
 *
 * <pre>
 * cd backend
 * mvn -pl grafioschtrader-server test -Dtest=PeriodPerformanceStoredVsLiveReportTest \
 *   -Dgt.period.performance.compare.tenant=7
 * </pre>
 *
 * <p>
 * <b>{@code @ActiveProfiles("prod")} is not optional.</b> Without an explicit profile the context boots the test
 * bootstrap and {@code V1__schema.sql} drops every table of the target database. Nothing here writes; the test runs in
 * a transaction that is rolled back. The live path is swapped into the report bean only for the duration of one call.
 * </p>
 */
//@formatter:on
@SpringBootTest(classes = GTforTest.class)
@ActiveProfiles("prod")
@Transactional
class PeriodPerformanceStoredVsLiveReportTest {

  private static final String TENANT_PROPERTY = "gt.period.performance.compare.tenant";

  /** Both ways read identical doubles, so anything above floating noise is a real difference. */
  private static final double TOLERANCE = 1e-9;

  /** Differences printed per comparison; the count covers all of them. */
  private static final int MAX_PRINTED = 15;

  /** Length of the range that crosses the marker. */
  private static final int CROSSING_DAYS = 400;

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

  @Autowired
  private PerformanceReport performanceReport;

  @Autowired
  private PeriodHoldingsLoader periodHoldingsLoader;

  @Autowired
  private HoldSecurityaccountSecurityJpaRepository holdSecurityaccountSecurityJpaRepository;

  @Autowired
  private HoldDailyTotalJpaRepository holdDailyTotalJpaRepository;

  @Autowired
  private HoldDailyTotalStateJpaRepository holdDailyTotalStateJpaRepository;

  @Autowired
  private TradingDaysPlusJpaRepository tradingDaysPlusJpaRepository;

  @Autowired
  private JsonMapper jsonMapper;

  private int comparisons;
  private int differences;

  @Test
  void compareStoredWithLive() throws Exception {
    String tenantProperty = System.getProperty(TENANT_PROPERTY);
    if (tenantProperty == null) {
      return;
    }
    Tenant tenant = tenantJpaRepository.findById(Integer.valueOf(tenantProperty)).orElseThrow();
    LocalDate today = LocalDate.now();
    LocalDate marker = holdDailyTotalStateJpaRepository.findById(tenant.getIdTenant())
        .map(HoldDailyTotalState::getRecalcFromDate).orElse(null);
    System.out.printf("Tenant %d, marker recalc_from_date %s%n", tenant.getIdTenant(), marker);
    compareScope(tenant, null, today);
    for (Portfolio portfolio : tenant.getPortfolioList()) {
      compareScope(tenant, portfolio, today);
    }
    System.out.printf("%nComparisons: %d, differing fields: %d%n", comparisons, differences);
    assertThat(differences).isZero();
  }

  private void compareScope(Tenant tenant, Portfolio portfolio, LocalDate today) throws Exception {
    Integer idTenant = tenant.getIdTenant();
    FirstAndMissingTradingDays days = portfolio == null
        ? performanceReport.getFirstAndMissingTradingDaysByTenant(idTenant, today)
        : performanceReport.getFirstAndMissingTradingDaysByPortfolio(idTenant, portfolio.getIdPortfolio(), today);
    if (days.latestTradingDay == null) {
      return;
    }
    TreeSet<LocalDate> valid = validDays(days);
    LocalDate last = valid.floor(days.latestTradingDay);
    if (valid.size() < 2 || last == null) {
      return;
    }
    String scope = portfolio == null ? "tenant " + idTenant : "portfolio " + portfolio.getIdPortfolio();
    compare(tenant, portfolio, today, scope, valid.first(), last, WeekYear.WM_YEAR);
    LocalDate yearEnd = valid.floor(LocalDate.of(last.getYear() - 1, 12, 31));
    LocalDate yearStart = valid.floor(LocalDate.of(last.getYear() - 2, 12, 31));
    if (yearStart != null && yearEnd != null && yearStart.isBefore(yearEnd)) {
      compare(tenant, portfolio, today, scope, yearStart, yearEnd, WeekYear.WM_YEAR);
      compare(tenant, portfolio, today, scope, yearStart, yearEnd, WeekYear.WM_WEEK);
    }
    LocalDate crossingStart = valid.ceiling(last.minusDays(CROSSING_DAYS));
    if (crossingStart != null && crossingStart.isBefore(last)) {
      compare(tenant, portfolio, today, scope, crossingStart, last, WeekYear.WM_YEAR);
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

  private void compare(Tenant tenant, Portfolio portfolio, LocalDate today, String scope, LocalDate dateFrom,
      LocalDate dateTo, WeekYear split) throws Exception {
    String label = String.format("%s %s..%s %s", scope, dateFrom, dateTo, split);
    try {
      long start = System.nanoTime();
      PerformancePeriod stored = report(tenant, portfolio, today, dateFrom, dateTo, split);
      long storedMillis = (System.nanoTime() - start) / 1_000_000;
      start = System.nanoTime();
      PerformancePeriod live = withLiveRows(() -> report(tenant, portfolio, today, dateFrom, dateTo, split));
      long liveMillis = (System.nanoTime() - start) / 1_000_000;
      comparisons++;
      List<String> diffs = new ArrayList<>();
      diff("", jsonMapper.valueToTree(stored), jsonMapper.valueToTree(live), diffs);
      differences += diffs.size();
      System.out.printf("%s: stored %d ms, live %d ms, %d differing fields%n", label, storedMillis, liveMillis,
          diffs.size());
      diffs.stream().limit(MAX_PRINTED).forEach(d -> System.out.println("  " + d));
    } catch (DataViolationException e) {
      System.out.printf("%s: skipped, %s%n", label, e.getMessage());
    }
  }

  private PerformancePeriod report(Tenant tenant, Portfolio portfolio, LocalDate today, LocalDate dateFrom,
      LocalDate dateTo, WeekYear split) throws Exception {
    return portfolio == null
        ? performanceReport.getPeriodPerformanceByTenant(tenant.getIdTenant(), "de", today, dateFrom, dateTo, split,
            false, null)
        : performanceReport.getPeriodPerformanceByPortfolio(tenant.getIdTenant(), "de", today,
            portfolio.getIdPortfolio(), dateFrom, dateTo, split, false, null);
  }

  /** Runs one report call with a loader that computes every day through the period holdings query. */
  private PerformancePeriod withLiveRows(ReportCall call) throws Exception {
    Object target = AopTestUtils.getUltimateTargetObject(performanceReport);
    PeriodHoldingsLoader liveOnly = new PeriodHoldingsLoader(holdSecurityaccountSecurityJpaRepository,
        holdDailyTotalJpaRepository, holdDailyTotalStateJpaRepository) {
      @Override
      public List<IPeriodHolding> load(Integer idTenant, Integer idPortfolio, LocalDate dateFrom, LocalDate dateTo) {
        return idPortfolio == null
            ? holdSecurityaccountSecurityJpaRepository.getPeriodHoldingsByTenant(idTenant, dateFrom, dateTo)
            : holdSecurityaccountSecurityJpaRepository.getPeriodHoldingsByPortfolio(idPortfolio, dateFrom, dateTo);
      }
    };
    ReflectionTestUtils.setField(target, "periodHoldingsLoader", liveOnly);
    try {
      return call.run();
    } finally {
      ReflectionTestUtils.setField(target, "periodHoldingsLoader", periodHoldingsLoader);
    }
  }

  /** Collects the paths at which two serialized results differ. */
  private static void diff(String path, JsonNode a, JsonNode b, List<String> diffs) {
    if (a.isNumber() && b.isNumber()) {
      if (Math.abs(a.asDouble() - b.asDouble()) > TOLERANCE) {
        diffs.add(path + ": stored " + a + ", live " + b);
      }
    } else if (a.isObject() && b.isObject()) {
      for (Iterator<Map.Entry<String, JsonNode>> it = a.properties().iterator(); it.hasNext();) {
        Map.Entry<String, JsonNode> field = it.next();
        JsonNode other = b.get(field.getKey());
        if (other == null) {
          diffs.add(path + "." + field.getKey() + ": missing in live");
        } else {
          diff(path + "." + field.getKey(), field.getValue(), other, diffs);
        }
      }
      b.properties().forEach(field -> {
        if (a.get(field.getKey()) == null) {
          diffs.add(path + "." + field.getKey() + ": missing in stored");
        }
      });
    } else if (a.isArray() && b.isArray()) {
      if (a.size() != b.size()) {
        diffs.add(path + ": stored " + a.size() + " elements, live " + b.size());
      }
      for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
        diff(path + "[" + i + "]", a.get(i), b.get(i), diffs);
      }
    } else if (!a.equals(b)) {
      diffs.add(path + ": stored " + a + ", live " + b);
    }
  }

  @FunctionalInterface
  private interface ReportCall {
    PerformancePeriod run() throws Exception;
  }
}
