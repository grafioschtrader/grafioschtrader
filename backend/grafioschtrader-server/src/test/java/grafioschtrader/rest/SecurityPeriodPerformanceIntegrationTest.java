package grafioschtrader.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafiosch.repository.UserJpaRepository;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.reports.PerformanceReport;
import grafioschtrader.reports.SecurityPeriodPerformanceReport;
import grafioschtrader.reportviews.performance.FirstAndMissingTradingDays;
import grafioschtrader.reportviews.performance.WeekYear;
import grafioschtrader.repository.PortfolioJpaRepository;

/**
 * Checks the period performance per security over HTTP on the populated fixture of the test database: both endpoints
 * under {@code /api/holding} resolve, the sum of the end values equals the security total of the period performance,
 * every complete row reconciles its own columns, and a portfolio of another tenant is refused.
 *
 * <p>
 * Run explicitly with {@code -Dgt.report.integration=true} after a roundtrip, on the tenant of {@code alledit}, which
 * holds securities from 2015 on and is the tenant Playwright spec 495 uses as well. The e2e backend must be stopped so
 * only this test application owns {@code grafioschtrader_t}, like {@link PerformanceReportPdfIntegrationTest}. The
 * completeness of the flows is proven against real data by {@code SecurityPeriodPerformanceVerificationTest}.
 * </p>
 */
@GTIntegrationTestContext
@EnabledIfSystemProperty(named = "gt.report.integration", matches = "true")
class SecurityPeriodPerformanceIntegrationTest extends BaseIntegrationTest {

  /** The period performance rounds its two sums to two digits, this report rounds once. */
  private static final double VALUE_TOLERANCE = 0.02;

  @Autowired
  private PerformanceReport performanceReport;

  @Autowired
  private SecurityPeriodPerformanceReport securityPeriodPerformanceReport;

  @Autowired
  private UserJpaRepository userJpaRepository;

  @Autowired
  private PortfolioJpaRepository portfolioJpaRepository;

  @Test
  @DisplayName("Values agree with the period performance, rows reconcile, both holding endpoints resolve")
  void agreesWithPeriodPerformance() throws Exception {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
    var user = userJpaRepository.findById(RestTestHelper.getUserByNickname(RestTestHelper.ALLEDIT).idUser)
        .orElseThrow();
    LocalDate today = LocalDate.now(ZoneId.of("UTC"));
    FirstAndMissingTradingDays days = performanceReport.getFirstAndMissingTradingDaysByTenant(user.getIdTenant(),
        today);
    LocalDate dateFrom = days.firstEverTradingDay;
    LocalDate dateTo = days.latestTradingDay.minusMonths(1);
    while (dateTo.getDayOfWeek().getValue() > 5 || days.isMissingQuoteDayOrHoliday(dateTo)) {
      dateTo = dateTo.minusDays(1);
    }
    assertTrue(dateFrom.isBefore(dateTo), "Historical security holdings fixture required");

    JsonNode securities = get(RequestGTMappings.HOLDING_MAP + "/securityperformance/" + dateFrom + "/" + dateTo);
    WeekYear periodSplit = ChronoUnit.MONTHS.between(dateFrom, dateTo) < days.minIncludeMonthLimit ? WeekYear.WM_WEEK
        : WeekYear.WM_YEAR;
    JsonNode period = get(RequestGTMappings.HOLDING_MAP + "/" + dateFrom + "/" + dateTo + "/" + periodSplit);
    assertFalse(securities.path("rows").isEmpty(), "Security transactions fixture required");

    JsonNode last = period.path("lastDayTotals");
    double expected = last.path("securitiesMC").asDouble() + last.path("marginCloseGainMC").asDouble();
    assertEquals(expected, securities.path("totals").path("valueEndMC").asDouble(), VALUE_TOLERANCE);
    for (JsonNode row : securities.path("rows")) {
      if (row.path("gainPeriodMC").isNumber()) {
        double reconciled = row.path("valueEndMC").asDouble() - row.path("valueStartMC").asDouble()
            - row.path("inflowMC").asDouble() + row.path("outflowMC").asDouble();
        assertEquals(reconciled, row.path("gainPeriodMC").asDouble(), 0.05, row.path("name").toString());
      }
    }
  }

  @Test
  @DisplayName("A portfolio of another tenant is refused")
  void foreignPortfolioRefused() {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
    var user = userJpaRepository.findById(RestTestHelper.getUserByNickname(RestTestHelper.ALLEDIT).idUser)
        .orElseThrow();
    // Called on the report, not over HTTP: the REST handler would count a security breach against the test user
    Portfolio foreign = portfolioJpaRepository.findAll().stream()
        .filter(p -> !p.getIdTenant().equals(user.getIdTenant())).findFirst().orElseThrow();
    LocalDate today = LocalDate.now(ZoneId.of("UTC"));
    assertThrows(SecurityException.class, () -> securityPeriodPerformanceReport.getByPortfolio(user.getIdTenant(),
        "en", today, foreign.getIdPortfolio(), today.minusYears(1), today));
  }

  private JsonNode get(String uri) throws Exception {
    String json = authenticatedClient(RestTestHelper.ALLEDIT).get().uri(uri).exchange().expectStatus().isOk()
        .expectBody(String.class).returnResult().getResponseBody();
    return new ObjectMapper().readTree(json);
  }
}
