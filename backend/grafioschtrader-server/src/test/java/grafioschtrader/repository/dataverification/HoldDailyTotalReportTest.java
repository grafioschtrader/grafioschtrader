package grafioschtrader.repository.dataverification;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.ToDoubleFunction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import grafioschtrader.entities.HoldDailyTotal;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.reportviews.performance.IPeriodHolding;
import grafioschtrader.repository.HoldDailyTotalJpaRepository;
import grafioschtrader.repository.HoldDailyTotalJpaRepositoryCustom.HoldDailyTotalSeries;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository;
import grafioschtrader.repository.PortfolioJpaRepository;
import grafioschtrader.service.HoldDailyTotalService;
import grafioschtrader.test.start.GTforTest;

/**
 * Report that proves {@code hold_daily_total} against the live period holdings queries it is derived from. For one
 * tenant it compares every stored row, on the tenant level and for every portfolio, with what
 * {@code getPeriodHoldingsByTenant} / {@code ...ByPortfolio} return over the whole stored history, and lists missing,
 * surplus and differing days.
 *
 * <p>
 * This is a maintainer utility against a real database, not a regression test. It is skipped unless the tenant is
 * named, and it runs the update first, timed, when asked to, which is how the duration of the first full-history run
 * is measured before the cron is enabled:
 * </p>
 *
 * <pre>
 * cd backend
 * mvn -pl grafioschtrader-server test -Dtest=HoldDailyTotalReportTest -Dgt.hold.daily.total.report.tenant=7 \
 *   -Dgt.hold.daily.total.report.update=true
 * </pre>
 *
 * <p>
 * The {@code prod} test profile does not run Flyway, so the database must already carry the two tables of migration
 * V0_38_1. Days from the marker {@code recalc_from_date} on are not compared, because they are allowed to be missing.
 * </p>
 */
@SpringBootTest(classes = GTforTest.class)
@ActiveProfiles("prod")
class HoldDailyTotalReportTest {

  private static final String TENANT_PROPERTY = "gt.hold.daily.total.report.tenant";
  private static final String UPDATE_PROPERTY = "gt.hold.daily.total.report.update";

  /** Every stored column is rounded to two digits by the query, so anything above half a cent is a real difference. */
  private static final double TOLERANCE = 0.005;

  private static final Logger log = LoggerFactory.getLogger(HoldDailyTotalReportTest.class);

  private static final Map<String, ToDoubleFunction<IPeriodHolding>> COLUMNS = Map.of("cashBalanceMC",
      IPeriodHolding::getCashBalanceMC, "securitiesMC", IPeriodHolding::getSecuritiesMC, "marginCloseGainMC",
      IPeriodHolding::getMarginCloseGainMC, "securityRiskMC", IPeriodHolding::getSecurityRiskMC,
      "externalCashTransferMC", IPeriodHolding::getExternalCashTransferMC, "dividendRealMC",
      IPeriodHolding::getDividendRealMC, "feeRealMC", IPeriodHolding::getFeeRealMC, "interestCashaccountRealMC",
      IPeriodHolding::getInterestCashaccountRealMC, "accumulateReduceMC", IPeriodHolding::getAccumulateReduceMC,
      "gainMC", IPeriodHolding::getGainMC);

  @Autowired
  private HoldDailyTotalService holdDailyTotalService;

  @Autowired
  private HoldDailyTotalJpaRepository holdDailyTotalJpaRepository;

  @Autowired
  private HoldSecurityaccountSecurityJpaRepository holdSecurityaccountSecurityJpaRepository;

  @Autowired
  private PortfolioJpaRepository portfolioJpaRepository;

  @Test
  @DisplayName("Every stored daily total equals the live period holdings query")
  void compareWithLiveQuery() {
    String tenantProperty = System.getProperty(TENANT_PROPERTY);
    assumeTrue(tenantProperty != null, "Set -D" + TENANT_PROPERTY + "=<idTenant> to run this report");
    Integer idTenant = Integer.valueOf(tenantProperty);

    if (Boolean.getBoolean(UPDATE_PROPERTY)) {
      long started = System.currentTimeMillis();
      int days = holdDailyTotalService.updateTenant(idTenant, LocalDate.now(ZoneOffset.UTC));
      log.info("Update of tenant {} computed {} days in {} ms", idTenant, days, System.currentTimeMillis() - started);
    }

    List<String> findings = new ArrayList<>();
    findings.addAll(compare(idTenant, null));
    for (Portfolio portfolio : portfolioJpaRepository.findByIdTenantOrderByName(idTenant)) {
      findings.addAll(compare(idTenant, portfolio.getIdPortfolio()));
    }
    findings.forEach(log::warn);
    log.info("hold_daily_total of tenant {}: {} findings", idTenant, findings.size());
    assertTrue(findings.isEmpty(), () -> findings.size() + " findings, the first: " + findings.getFirst());
  }

  private List<String> compare(Integer idTenant, Integer idPortfolio) {
    String scope = idPortfolio == null ? "tenant " + idTenant : "portfolio " + idPortfolio;
    HoldDailyTotalSeries series = holdDailyTotalJpaRepository.getSeries(idTenant, idPortfolio, LocalDate.of(1900, 1, 1),
        LocalDate.of(9999, 12, 31));
    List<String> findings = new ArrayList<>();
    if (series.recalcFromDate() == null) {
      findings.add(scope + ": the tenant has never been computed");
      return findings;
    }
    if (series.rows().isEmpty()) {
      log.info("{}: no rows stored", scope);
      return findings;
    }
    LocalDate fromDate = series.rows().getFirst().getHoldDate();
    LocalDate toDate = series.recalcFromDate().minusDays(1);
    TreeMap<LocalDate, IPeriodHolding> live = loadLive(idPortfolio == null ? idTenant : idPortfolio,
        idPortfolio == null, fromDate, toDate);
    TreeMap<LocalDate, HoldDailyTotal> stored = new TreeMap<>();
    series.rows().stream().filter(r -> !r.getHoldDate().isAfter(toDate)).forEach(r -> stored.put(r.getHoldDate(), r));

    live.keySet().stream().filter(d -> !stored.containsKey(d)).forEach(d -> findings.add(scope + " " + d + ": missing"));
    stored.keySet().stream().filter(d -> !live.containsKey(d)).forEach(d -> findings.add(scope + " " + d + ": surplus"));
    stored.forEach((date, row) -> {
      IPeriodHolding expected = live.get(date);
      if (expected != null) {
        COLUMNS.forEach((column, getter) -> {
          if (Math.abs(getter.applyAsDouble(row) - getter.applyAsDouble(expected)) > TOLERANCE) {
            findings.add(scope + " " + date + ": " + column + " stored " + getter.applyAsDouble(row) + " live "
                + getter.applyAsDouble(expected));
          }
        });
      }
    });
    log.info("{}: {} stored days compared from {} to {}", scope, stored.size(), fromDate, toDate);
    return findings;
  }

  /** The live rows of the range, with the zero base row the update adds when the regular query lacks it. */
  private TreeMap<LocalDate, IPeriodHolding> loadLive(Integer id, boolean tenant, LocalDate fromDate,
      LocalDate toDate) {
    TreeMap<LocalDate, IPeriodHolding> live = new TreeMap<>();
    (tenant ? holdSecurityaccountSecurityJpaRepository.getPeriodHoldingsByTenant(id, fromDate, toDate)
        : holdSecurityaccountSecurityJpaRepository.getPeriodHoldingsByPortfolio(id, fromDate, toDate))
        .forEach(h -> live.put(h.getDate(), h));
    LocalDate zeroBase = tenant ? holdSecurityaccountSecurityJpaRepository.getZeroBaseDateByTenant(id)
        : holdSecurityaccountSecurityJpaRepository.getZeroBaseDateByPortfolio(id);
    if (zeroBase != null && !zeroBase.isBefore(fromDate) && !zeroBase.isAfter(toDate) && !live.containsKey(zeroBase)) {
      (tenant ? holdSecurityaccountSecurityJpaRepository.getPeriodHoldingZeroBaseByTenant(id, zeroBase)
          : holdSecurityaccountSecurityJpaRepository.getPeriodHoldingZeroBaseByPortfolio(id, zeroBase))
          .forEach(h -> live.put(h.getDate(), h));
    }
    return live;
  }
}
