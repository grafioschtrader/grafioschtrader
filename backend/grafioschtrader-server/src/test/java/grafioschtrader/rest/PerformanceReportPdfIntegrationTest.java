package grafioschtrader.rest;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Set;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;

import com.fasterxml.jackson.databind.ObjectMapper;

import grafiosch.repository.UserJpaRepository;
import grafioschtrader.dto.PerformanceReportRequest;
import grafioschtrader.report.pdf.PerformanceReportPdfService;
import grafioschtrader.report.pdf.ReportContext;
import grafioschtrader.report.pdf.ReportFormatter;
import grafioschtrader.reports.PerformanceReport;
import grafioschtrader.reportviews.performance.WeekYear;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.PerformanceReportPreset;
import grafioschtrader.types.PerformanceReportSection;

/**
 * Compares a context-free service call with the authenticated screen result on the populated foreign-currency fixture.
 * Run explicitly with -Dgt.report.integration=true after security transactions have been created (Playwright 075 or
 * later), with the e2e backend stopped so only this test application owns grafioschtrader_t. This opt-in prevents the
 * ordinary unit run from bootstrapping a database or accidentally executing before the interleaved fixtures exist.
 */
@GTIntegrationTestContext
@EnabledIfSystemProperty(named = "gt.report.integration", matches = "true")
class PerformanceReportPdfIntegrationTest extends BaseIntegrationTest {
  @Autowired
  private PerformanceReportPdfService pdfService;
  @Autowired
  private PerformanceReport performance;
  @Autowired
  private UserJpaRepository users;
  @Autowired
  private CurrencypairJpaRepository currencies;
  @Autowired
  private MessageSource messages;
  @Autowired
  private GlobalparametersService parameters;

  @Test
  @DisplayName("Historical foreign-currency PDF generated without request/security context agrees with the REST screen")
  void historicalReportMatchesScreen() throws Exception {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
    var user = users.findById(RestTestHelper.getUserByNickname(RestTestHelper.ADMIN).idUser).orElseThrow();
    assertFalse(currencies.getAllCurrencypairsForTenantByTenant(user.getIdTenant()).isEmpty(),
        "Foreign-currency fixture required");
    var today = LocalDate.now(ZoneId.of("UTC"));
    var days = performance.getFirstAndMissingTradingDaysByTenant(user.getIdTenant(), today);
    var request = new PerformanceReportRequest();
    request.dateFrom = days.firstEverTradingDay;
    request.dateTo = days.latestTradingDay.minusMonths(1);
    while (request.dateTo.getDayOfWeek().getValue() > 5 || days.isMissingQuoteDayOrHoliday(request.dateTo)) {
      request.dateTo = request.dateTo.minusDays(1);
    }
    assertTrue(request.dateFrom.isBefore(request.dateTo), "Historical security holdings fixture required");
    request.periodSplit = ChronoUnit.MONTHS.between(request.dateFrom, request.dateTo) < days.minIncludeMonthLimit
        ? WeekYear.WM_WEEK
        : WeekYear.WM_YEAR;
    request.preset = PerformanceReportPreset.CUSTOM;
    request.sections = Set.of(PerformanceReportSection.PERFORMANCE_SUMMARY, PerformanceReportSection.RISK_COST_METRICS,
        PerformanceReportSection.INCOME_COSTS, PerformanceReportSection.TRANSACTIONS);
    request.language = "en";
    request.numberFormat = "de-CH";
    String screenJson = authenticatedClient(RestTestHelper.ADMIN).get()
        .uri(RequestGTMappings.HOLDING_MAP + "/" + request.dateFrom + "/" + request.dateTo + "/" + request.periodSplit)
        .exchange().expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody();
    var screen = new ObjectMapper().readTree(screenJson);
    SecurityContextHolder.clearContext();
    RequestContextHolder.resetRequestAttributes();
    var context = new ReportContext(user.getIdTenant(), null, Locale.ENGLISH, Locale.forLanguageTag("de-CH"), today,
        ZoneId.of("UTC"));
    byte[] bytes = pdfService.generate(context, request);
    var precision = parameters.getCurrencyPrecision();
    var formatter = new ReportFormatter(context.language(), context.numberFormat(), messages,
        c -> precision.getOrDefault(c, 2));
    try (var document = Loader.loadPDF(bytes)) {
      String text = new PDFTextStripper().getText(document);
      for (String field : new String[] { "totalBalanceMC", "totalGainMC", "externalCashTransferMC",
          "marginCloseGainMC" }) {
        for (String level : new String[] { "firstDayTotals", "lastDayTotals", "difference" }) {
          assertTrue(text.contains(formatter.money(screen.path(level).path(field).asDouble(), "CHF")),
              level + "." + field);
        }
      }
      assertTrue(text.contains(formatter.percent(screen.path("metrics").path("twrPercent").asDouble())));
      assertTrue(text.contains("Costs recorded in GT"));
      assertTrue(text.contains("Amount in account currency"));
      if (screen.path("metrics").path("feesWithoutRate").asInt() == 0) {
        assertTrue(text.contains(formatter.money(-screen.path("metrics").path("feesMC").asDouble(), "CHF")));
      }
    }
    authenticatedClient(RestTestHelper.ADMIN).post().uri(RequestGTMappings.HOLDING_MAP + "/report/pdf").body(request)
        .exchange().expectStatus().isOk().expectHeader().contentType("application/pdf").expectHeader()
        .valueEquals("Cache-Control", "no-store");
    request.sections = Set.of(PerformanceReportSection.HOLDINGS);
    request.dateFrom = null;
    request.reportDate = request.dateTo;
    request.dateTo = null;
    request.periodSplit = null;
    String holdingsJson = authenticatedClient(RestTestHelper.ADMIN).get()
        .uri(RequestGTMappings.SECURITYACCOUNT_MAP
            + "/tenantsecurityaccountsummary/assetclasstypewithcash?includeClosedPosition=false&untilDate="
            + request.reportDate)
        .exchange().expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody();
    double statementTotal = new ObjectMapper().readTree(holdingsJson).path("grandAccountValueSecurityMC").asDouble();
    try (var document = Loader.loadPDF(pdfService.generate(context, request))) {
      assertTrue(new PDFTextStripper().getText(document).contains(formatter.money(statementTotal, "CHF")));
    }
    authenticatedClient(RestTestHelper.ADMIN).post().uri(RequestGTMappings.HOLDING_MAP + "/report/pdf").body(request)
        .exchange().expectStatus().isOk().expectHeader().contentType("application/pdf");
    request.sections = Set.of(PerformanceReportSection.TRANSACTIONS);
    authenticatedClient(RestTestHelper.ADMIN).post().uri(RequestGTMappings.HOLDING_MAP + "/report/pdf").body(request)
        .exchange().expectStatus().isBadRequest();
  }
}
