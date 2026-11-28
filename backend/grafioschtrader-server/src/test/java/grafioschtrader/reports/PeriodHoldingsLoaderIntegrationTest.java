package grafioschtrader.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import grafioschtrader.entities.HoldDailyTotalState;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.reportviews.performance.IPeriodHolding;
import grafioschtrader.repository.HoldDailyTotalStateJpaRepository;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository;
import grafioschtrader.repository.PortfolioJpaRepository;
import grafioschtrader.rest.GTIntegrationTestContext;
import grafioschtrader.service.HoldDailyTotalService;

//@formatter:off
/**
 * Proves that {@link PeriodHoldingsLoader} returns exactly what the period holdings query returns, although it takes
 * most days from {@code hold_daily_total}, and that it never takes a stored day that may be outdated.
 *
 * <p>
 * The first test compares the loader with the live query for every computed tenant of {@code grafioschtrader_t} and
 * each of its portfolios, starting before the zero base so that the cash-only days in front of the stored rows are
 * covered too. The other two change a stored row of {@code alledit} on purpose: before the marker the loader must
 * return the changed row, which proves that the table is read at all; once the marker is lowered, or once a price of
 * that day is younger than the last scan of the task, it must return the live value again. The task is run afterwards,
 * which recomputes the changed row.
 * </p>
 *
 * <p>
 * Opt in with {@code -Dgt.report.integration=true} after a roundtrip; the e2e backend must be stopped so only this test
 * application owns {@code grafioschtrader_t}.
 * </p>
 */
//@formatter:on
@GTIntegrationTestContext
@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
@EnabledIfSystemProperty(named = "gt.report.integration", matches = "true")
class PeriodHoldingsLoaderIntegrationTest {

  /** The query rounds every column to two digits. */
  private static final double TOLERANCE = 0.005;

  /** Added to the result of a stored row, far more than any rounding. */
  private static final double TAMPER = 1000.0;

  private static final Map<String, ToDoubleFunction<IPeriodHolding>> COLUMNS = Map.of("cashBalanceMC",
      IPeriodHolding::getCashBalanceMC, "securitiesMC", IPeriodHolding::getSecuritiesMC, "marginCloseGainMC",
      IPeriodHolding::getMarginCloseGainMC, "securityRiskMC", IPeriodHolding::getSecurityRiskMC,
      "externalCashTransferMC", IPeriodHolding::getExternalCashTransferMC, "dividendRealMC",
      IPeriodHolding::getDividendRealMC, "feeRealMC", IPeriodHolding::getFeeRealMC, "interestCashaccountRealMC",
      IPeriodHolding::getInterestCashaccountRealMC, "accumulateReduceMC", IPeriodHolding::getAccumulateReduceMC,
      "gainMC", IPeriodHolding::getGainMC);

  @Autowired
  private PeriodHoldingsLoader periodHoldingsLoader;

  @Autowired
  private HoldSecurityaccountSecurityJpaRepository holdSecurityaccountSecurityJpaRepository;

  @Autowired
  private HoldDailyTotalService holdDailyTotalService;

  @Autowired
  private HoldDailyTotalStateJpaRepository holdDailyTotalStateJpaRepository;

  @Autowired
  private PortfolioJpaRepository portfolioJpaRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private final LocalDate today = LocalDate.now(ZoneOffset.UTC);

  private Integer idAlledit;

  /** A stored tenant day of alledit with a price of a held security, well before the marker. */
  private LocalDate tamperedDay;

  @BeforeAll
  void computeDailyTotals() {
    idAlledit = jdbcTemplate.queryForObject("SELECT id_tenant FROM tenant WHERE tenant_name = 'Tenant alledit'",
        Integer.class);
    for (Integer idTenant : computedTenants()) {
      holdDailyTotalService.updateTenant(idTenant, today);
    }
    tamperedDay = jdbcTemplate.queryForObject("""
        SELECT MAX(h.hold_date) FROM hold_daily_total h WHERE h.id_tenant = ? AND h.id_portfolio IS NULL
        AND h.hold_date < DATE_SUB(?, INTERVAL 1 YEAR) AND EXISTS (SELECT 1 FROM hold_securityaccount_security hss
        JOIN historyquote q ON q.id_securitycurrency = hss.id_securitycurrency AND q.date = h.hold_date
        WHERE hss.id_tenant = h.id_tenant AND hss.from_hold_date <= h.hold_date
        AND (hss.to_hold_date IS NULL OR hss.to_hold_date >= h.hold_date))""", LocalDate.class, idAlledit, today);
  }

  /** Lowers the marker first, so the changed row is recomputed even when a test failed before the task ran. */
  @AfterAll
  void recomputeDailyTotals() {
    if (tamperedDay != null) {
      jdbcTemplate.update("UPDATE hold_daily_total_state SET recalc_from_date = LEAST(recalc_from_date, ?) "
          + "WHERE id_tenant = ?", tamperedDay, idAlledit);
    }
    holdDailyTotalService.updateTenant(idAlledit, today);
  }

  @Test
  @Order(1)
  @DisplayName("Stored and live days together equal the live query for every tenant and portfolio")
  void loaderEqualsLiveQuery() {
    List<Integer> tenants = computedTenants();
    assertThat(tenants).isNotEmpty();
    for (Integer idTenant : tenants) {
      LocalDate from = jdbcTemplate.queryForObject(
          "SELECT MIN(from_hold_date) FROM hold_cashaccount_balance WHERE id_tenant = ?", LocalDate.class, idTenant);
      assertSame("tenant " + idTenant, periodHoldingsLoader.load(idTenant, null, from, today),
          holdSecurityaccountSecurityJpaRepository.getPeriodHoldingsByTenant(idTenant, from, today));
      for (Portfolio portfolio : portfolioJpaRepository.findByIdTenantOrderByName(idTenant)) {
        Integer idPortfolio = portfolio.getIdPortfolio();
        assertSame("portfolio " + idPortfolio, periodHoldingsLoader.load(idTenant, idPortfolio, from, today),
            holdSecurityaccountSecurityJpaRepository.getPeriodHoldingsByPortfolio(idPortfolio, from, today));
      }
    }
  }

  @Test
  @Order(2)
  @DisplayName("A stored day is read before the marker and computed again once the marker is lowered")
  void storedDayIsUsedOnlyBeforeTheMarker() {
    double live = liveGain(tamperedDay);
    tamper(tamperedDay);
    assertThat(loadedGain(tamperedDay)).isCloseTo(live + TAMPER, within(TOLERANCE));

    jdbcTemplate.update("UPDATE hold_daily_total_state SET recalc_from_date = ? WHERE id_tenant = ?", tamperedDay,
        idAlledit);
    assertThat(loadedGain(tamperedDay)).isCloseTo(live, within(TOLERANCE));
    holdDailyTotalService.updateTenant(idAlledit, today);
    assertThat(loadedGain(tamperedDay)).isCloseTo(live, within(TOLERANCE));
  }

  @Test
  @Order(3)
  @DisplayName("A price younger than the last scan of the task makes its day and the following ones live")
  void backFilledPriceMakesTheDayLive() {
    double live = liveGain(tamperedDay);
    tamper(tamperedDay);
    assertThat(loadedGain(tamperedDay)).isCloseTo(live + TAMPER, within(TOLERANCE));

    HoldDailyTotalState state = holdDailyTotalStateJpaRepository.findById(idAlledit).orElseThrow();
    Map<String, Object> quote = jdbcTemplate.queryForMap("""
        SELECT q.id_history_quote, q.create_modify_time FROM historyquote q JOIN hold_securityaccount_security hss
        ON q.id_securitycurrency = hss.id_securitycurrency WHERE hss.id_tenant = ? AND q.date = ?
        AND hss.from_hold_date <= q.date AND (hss.to_hold_date IS NULL OR hss.to_hold_date >= q.date) LIMIT 1""",
        idAlledit, tamperedDay);
    jdbcTemplate.update("UPDATE historyquote SET create_modify_time = ? WHERE id_history_quote = ?",
        state.getQuoteCheckedTime().plusMinutes(1), quote.get("id_history_quote"));
    try {
      assertThat(loadedGain(tamperedDay)).isCloseTo(live, within(TOLERANCE));
      holdDailyTotalService.updateTenant(idAlledit, today);
    } finally {
      jdbcTemplate.update("UPDATE historyquote SET create_modify_time = ? WHERE id_history_quote = ?",
          quote.get("create_modify_time"), quote.get("id_history_quote"));
    }
    assertThat(loadedGain(tamperedDay)).isCloseTo(live, within(TOLERANCE));
  }

  private List<Integer> computedTenants() {
    return jdbcTemplate.queryForList("SELECT id_tenant FROM hold_daily_total_state ORDER BY id_tenant", Integer.class);
  }

  private void tamper(LocalDate day) {
    int rows = jdbcTemplate.update("""
        UPDATE hold_daily_total SET gain_mc = gain_mc + ? WHERE id_tenant = ? AND id_portfolio IS NULL
        AND hold_date = ?""", TAMPER, idAlledit, day);
    assertThat(rows).isEqualTo(1);
  }

  private double liveGain(LocalDate day) {
    return single(holdSecurityaccountSecurityJpaRepository.getPeriodHoldingsByTenant(idAlledit, day, day)).getGainMC();
  }

  /** Loads a range long enough for the stored rows and returns its last day, which must be the given one. */
  private double loadedGain(LocalDate day) {
    List<IPeriodHolding> holdings = periodHoldingsLoader.load(idAlledit, null,
        day.minusDays(PeriodHoldingsLoader.MIN_STORED_RANGE_DAYS), day);
    assertThat(holdings.getLast().getDate()).isEqualTo(day);
    return holdings.getLast().getGainMC();
  }

  private static IPeriodHolding single(List<IPeriodHolding> holdings) {
    assertThat(holdings).hasSize(1);
    return holdings.getFirst();
  }

  private static void assertSame(String scope, List<IPeriodHolding> loaded, List<IPeriodHolding> live) {
    assertThat(loaded.stream().map(IPeriodHolding::getDate).toList()).as(scope + " days")
        .isEqualTo(live.stream().map(IPeriodHolding::getDate).toList());
    for (int i = 0; i < live.size(); i++) {
      for (Map.Entry<String, ToDoubleFunction<IPeriodHolding>> column : COLUMNS.entrySet()) {
        assertThat(column.getValue().applyAsDouble(loaded.get(i))).as(scope + " " + live.get(i).getDate() + " "
            + column.getKey()).isCloseTo(column.getValue().applyAsDouble(live.get(i)), within(TOLERANCE));
      }
    }
  }
}
