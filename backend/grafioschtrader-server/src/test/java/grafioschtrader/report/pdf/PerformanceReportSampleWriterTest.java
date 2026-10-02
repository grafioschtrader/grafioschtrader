package grafioschtrader.report.pdf;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.core.context.SecurityContextHolder;

import grafiosch.repository.TaskDataChangeJpaRepository;
import grafioschtrader.dto.PerformanceReportRequest;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Tenant;
import grafioschtrader.reports.PerformanceReport;
import grafioschtrader.reportviews.performance.FirstAndMissingTradingDays;
import grafioschtrader.reportviews.performance.IDailyExternalFlow;
import grafioschtrader.reportviews.performance.IPeriodHolding;
import grafioschtrader.reportviews.performance.PerformancePeriod;
import grafioschtrader.reportviews.performance.PeriodHoldingAndDiff;
import grafioschtrader.reportviews.performance.WeekYear;
import grafioschtrader.repository.PortfolioJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.PerformanceReportPreset;
import jakarta.validation.Validation;

/**
 * Deterministic, database-free reports and page images for visual QA; all figures go through the real period builder.
 */
class PerformanceReportSampleWriterTest {
  static final LocalDate FROM = LocalDate.of(2024, 12, 31);
  static final LocalDate TO = LocalDate.of(2025, 12, 31);

  record Holding(LocalDate date, double cash, double securities, double margin, double deposits)
      implements IPeriodHolding {
    public LocalDate getDate() {
      return date;
    }

    public double getCashBalanceMC() {
      return cash;
    }

    public double getSecuritiesMC() {
      return securities;
    }

    public double getMarginCloseGainMC() {
      return margin;
    }

    public double getExternalCashTransferMC() {
      return deposits;
    }

    public double getGainMC() {
      return cash + securities - deposits;
    }

    public double getDividendRealMC() {
      return 0;
    }

    public double getFeeRealMC() {
      return 0;
    }

    public double getInterestCashaccountRealMC() {
      return 0;
    }

    public double getAccumulateReduceMC() {
      return 0;
    }

    public double getSecurityRiskMC() {
      return securities * 2;
    }
  }

  record Flow(LocalDate date, double amount) implements IDailyExternalFlow {
    public LocalDate getFlowDate() {
      return date;
    }

    public double getFlowMC() {
      return amount;
    }
  }

  static ResourceBundleMessageSource messages() {
    var source = new ResourceBundleMessageSource();
    source.setBasenames("message/messages", "i18n/messages");
    source.setDefaultEncoding("UTF-8");
    source.setFallbackToSystemLocale(false);
    return source;
  }

  static FirstAndMissingTradingDays days(LocalDate from, LocalDate to) {
    return new FirstAndMissingTradingDays(from, from.plusDays(1), from.plusDays(1), from, to, to, to.minusDays(1),
        Set.of(), Set.of());
  }

  static PerformancePeriod period(WeekYear split, List<IPeriodHolding> holdings, List<IDailyExternalFlow> flows,
      int skippedFees) throws Exception {
    var first = new PeriodHoldingAndDiff();
    var last = new PeriodHoldingAndDiff();
    BeanUtils.copyProperties(holdings.getFirst(), first);
    BeanUtils.copyProperties(holdings.getLast(), last);
    var period = new PerformancePeriod(split, first, last, last.calculateDiff(first));
    period.createPeriodWindows(days(first.getDate(), last.getDate()), holdings, flows, 12.5, skippedFees);
    return period;
  }

  static PerformanceReportRequest request(String language, PerformanceReportPreset preset) {
    var request = new PerformanceReportRequest();
    request.language = language;
    request.numberFormat = "de-CH";
    request.preset = preset;
    request.sections = preset.getSections();
    request.dateFrom = FROM;
    request.dateTo = TO;
    request.periodSplit = WeekYear.WM_YEAR;
    request.recipient = "Frau Müller\nVermögensverwaltung\nBahnhofstrasse 10\n8001 Zürich";
    request.sender = "Advisory AG\nZürich";
    return request;
  }

  static List<SectionRenderer> renderers() {
    return List.of(new CoverRenderer(), new PerformanceSummaryRenderer(), new PerformanceChartsRenderer(),
        new AnnualReturnsRenderer(), new PeriodWindowsRenderer(), new RiskCostMetricsRenderer(), new GlossaryRenderer(),
        new HoldingsRenderer(), new AllocationRenderer(), new IncomeCostsRenderer(), new TransactionsRenderer());
  }

  static PerformanceReportPdfService service(PerformanceReport performance, TenantJpaRepository tenants,
      PortfolioJpaRepository portfolios, TaskDataChangeJpaRepository tasks) {
    var parameters = mock(GlobalparametersService.class);
    when(parameters.getCurrencyPrecision()).thenReturn(Map.of("CHF", 2));
    return new PerformanceReportPdfService(tenants, portfolios, tasks, performance, parameters, messages(),
        new PerformanceReportValidation(Validation.buildDefaultValidatorFactory().getValidator()), renderers(),
        mock(grafioschtrader.reports.SecurityGroupByAssetclassWithCashReport.class), mock(BookingDataLoader.class));
  }

  static byte[] generate(PerformancePeriod period, PerformanceReportRequest request) throws Exception {
    return generate(period, request, null);
  }

  static byte[] generate(PerformancePeriod period, PerformanceReportRequest request,
      grafioschtrader.reportviews.securityaccount.SecurityPositionGrandSummary statement) throws Exception {
    return generate(period, request, statement, null, null);
  }

  static byte[] generate(PerformancePeriod period, PerformanceReportRequest request,
      grafioschtrader.reportviews.securityaccount.SecurityPositionGrandSummary statement,
      grafioschtrader.reportviews.securityaccount.SecurityPositionGrandSummary opening, BookingData bookings)
      throws Exception {
    var performance = mock(PerformanceReport.class);
    var tenants = mock(TenantJpaRepository.class);
    var tenant = new Tenant();
    tenant.setTenantName("Müller & Partner");
    tenant.setCurrency("CHF");
    tenant.setIdTenant(7);
    when(tenants.findById(7)).thenReturn(java.util.Optional.of(tenant));
    // Detached test data has no collections; a mock supplies the immutable portfolio list.
    tenant = spy(tenant);
    when(tenant.getPortfolioList()).thenReturn(List.<Portfolio>of());
    when(tenants.findById(7)).thenReturn(java.util.Optional.of(tenant));
    when(performance.getFirstAndMissingTradingDaysByTenant(eq(7), any()))
        .thenReturn(days(request.dateFrom, request.dateTo));
    when(performance.getPeriodPerformanceByTenant(eq(7), anyString(), any(), any(), any(), any(), anyBoolean()))
        .thenReturn(period);
    SecurityContextHolder.clearContext();
    org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
    var service = service(performance, tenants, mock(PortfolioJpaRepository.class),
        mock(TaskDataChangeJpaRepository.class));
    if (statement != null) {
      var loader = mock(grafioschtrader.reports.SecurityGroupByAssetclassWithCashReport.class);
      when(loader.getSecurityPositionGrandSummaryIdTenant(eq(7), eq(false), eq(request.dateTo), any()))
          .thenReturn(statement);
      if (opening != null) {
        when(loader.getSecurityPositionGrandSummaryIdTenant(eq(7), eq(false), eq(request.dateFrom), any()))
            .thenReturn(opening);
      }
      org.springframework.test.util.ReflectionTestUtils.setField(service, "holdings", loader);
    }
    if (bookings != null) {
      var loader = mock(BookingDataLoader.class);
      when(loader.load(any(), any(), any(), anyString(), anyBoolean(), anyBoolean(), any(), any()))
          .thenReturn(bookings);
      org.springframework.test.util.ReflectionTestUtils.setField(service, "bookings", loader);
    }
    byte[] result = service.generate(new ReportContext(7, null, Locale.forLanguageTag(request.language),
        Locale.forLanguageTag(request.numberFormat), TO.plusDays(20), ZoneId.of("Europe/Zurich")), request);
    if (opening != null) {
      var loader = (grafioschtrader.reports.SecurityGroupByAssetclassWithCashReport) org.springframework.test.util.ReflectionTestUtils
          .getField(service, "holdings");
      verify(loader).getSecurityPositionGrandSummaryIdTenant(7, false, request.dateFrom, TO.plusDays(20));
      verify(loader).getSecurityPositionGrandSummaryIdTenant(7, false, request.dateTo, TO.plusDays(20));
      verifyNoMoreInteractions(loader);
    }
    return result;
  }

  @Test
  @DisplayName("Missing booking rates are visible as n/a and an unexplained remainder is labelled without claiming FX profit")
  void bookingDataStatusAndReconciliation() throws Exception {
    var request = request("en", PerformanceReportPreset.CUSTOM);
    request.sections = Set.of(grafioschtrader.types.PerformanceReportSection.INCOME_COSTS,
        grafioschtrader.types.PerformanceReportSection.TRANSACTIONS,
        grafioschtrader.types.PerformanceReportSection.HOLDINGS);
    var period = period(WeekYear.WM_YEAR,
        List.of(new Holding(FROM, 1000, 0, 0, 1000), new Holding(TO, 1010, 0, 0, 1000)), List.of(), 0);
    var fee = BookingDataTest.booking(1, grafioschtrader.types.TransactionType.FEE, null, "GBP", -10);
    var b = BookingData.calculate(List.of(fee), null, null, BookingDataTest.rates(false), false);
    try (var document = Loader.loadPDF(
        generate(period, request, HoldingsReportTest.summary(List.of()), HoldingsReportTest.summary(List.of()), b))) {
      String text = new PDFTextStripper().getText(document);
      assertTrue(text.contains("Missing conversion rates for 1 period bookings"), text);
      assertTrue(text.contains("Total recorded costs: n/a"), text);
      assertTrue(text.contains("Recorded cost ratio: n/a"), text);
      assertTrue(text.contains("Not attributed difference: n/a"), text);
    }
    b = BookingData.calculate(List.of(), null, null, BookingDataTest.rates(false), false);
    try (var document = Loader.loadPDF(
        generate(period, request, HoldingsReportTest.summary(List.of()), HoldingsReportTest.summary(List.of()), b))) {
      String text = new PDFTextStripper().getText(document);
      assertTrue(text.contains("Not attributed difference: 10.00"), text);
      assertTrue(text.contains("revaluation of foreign-currency cash"), text);
      assertTrue(text.contains("accrued interest and rounding"), text);
    }
  }

  static byte[] bookingSample(String language, int count) throws Exception {
    var request = request(language, PerformanceReportPreset.CUSTOM);
    request.sections = Set.of(grafioschtrader.types.PerformanceReportSection.COVER,
        grafioschtrader.types.PerformanceReportSection.INCOME_COSTS,
        grafioschtrader.types.PerformanceReportSection.TRANSACTIONS,
        grafioschtrader.types.PerformanceReportSection.GLOSSARY);
    var transactions = new ArrayList<grafioschtrader.entities.Transaction>();
    var s = BookingDataTest.security(1, count > 20 ? "Müller International " + "W".repeat(100) : "Müller International",
        "CHF");
    for (int i = 1; i <= count; i++) {
      transactions.add(BookingDataTest.booking(i, grafioschtrader.types.TransactionType.DIVIDEND, s, "CHF", 12.5));
    }
    var transfer = BookingDataTest.booking(count + 1, grafioschtrader.types.TransactionType.REDUCE, s, "CHF", 100);
    transfer.setIdSecurityTransfer(1);
    transactions.add(transfer);
    var buy = BookingDataTest.booking(count + 2, grafioschtrader.types.TransactionType.ACCUMULATE, s, "CHF", -100);
    buy.setIdSecurityTransfer(1);
    transactions.add(buy);
    transactions.add(BookingDataTest.booking(count + 3, grafioschtrader.types.TransactionType.FEE, null, "CHF", -12.5));
    var cash = BookingDataTest.booking(count + 4, grafioschtrader.types.TransactionType.DEPOSIT, null, "CHF", 100);
    cash.setConnectedIdTransaction(1000);
    transactions.add(cash);
    var b = BookingData.calculate(transactions, null, null, BookingDataTest.rates(false), false);
    var period = period(WeekYear.WM_YEAR,
        List.of(new Holding(FROM, 10000, 0, 0, 10000), new Holding(TO, 10000 + 12.5 * count - 12.5, 0, 0, 10000)),
        List.of(), 0);
    return generate(period, request, HoldingsReportTest.summary(List.of()), HoldingsReportTest.summary(List.of()), b);
  }

  @Test
  @DisplayName("Compact and long booking reports preserve totals, transfers, contents and numbering in EN/DE and greyscale")
  void bookingSamples() throws Exception {
    Path dir = Path.of("target/performance-report-samples");
    Files.createDirectories(dir);
    for (String language : List.of("de", "en")) {
      for (int count : List.of(2, 90)) {
        byte[] bytes = bookingSample(language, count);
        String name = "bookings_" + count + "_" + language;
        Files.write(dir.resolve(name + ".pdf"), bytes);
        try (var document = Loader.loadPDF(bytes)) {
          String text = new PDFTextStripper().getText(document);
          assertTrue(text.contains(count == 2 ? "25.00" : "1’125.00"), text);
          assertTrue(text.contains("12.50"));
          assertTrue(text.contains("[T]"));
          assertTrue(text.contains(language.equals("de") ? "Kontoübertrag" : "Cash transfer"));
          assertTrue(text.contains(language.equals("de") ? "Wertschriftenübertrag" : "Security transfer"));
          assertFalse(
              text.contains(language.equals("de") ? "Nicht zugeordnete Differenz" : "Not attributed difference"));
          assertEquals(3, document.getPage(0).getAnnotations().size());
          var renderer = new PDFRenderer(document);
          for (int page = 0; page < document.getNumberOfPages(); page++) {
            assertTrue(text.contains(
                (language.equals("de") ? "Seite " : "Page ") + (page + 1) + " / " + document.getNumberOfPages()));
            ImageIO.write(renderer.renderImageWithDPI(page, 100), "png",
                dir.resolve(name + "_" + (page + 1) + ".png").toFile());
            ImageIO.write(renderer.renderImageWithDPI(page, 72, org.apache.pdfbox.rendering.ImageType.GRAY), "png",
                dir.resolve(name + "_gray_" + (page + 1) + ".png").toFile());
          }
        }
      }
    }
  }

  @Test
  @DisplayName("Render all delivered sections in DE and EN with searchable Unicode and complete page counts")
  void writeSamples() throws Exception {
    List<IPeriodHolding> holdings = new ArrayList<>();
    List<IDailyExternalFlow> flows = new ArrayList<>();
    int index = 0;
    for (LocalDate date = FROM; !date.isAfter(TO); date = date.plusDays(1)) {
      if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
        continue;
      }
      double deposit = date.getMonthValue() >= 7 && date.getYear() == 2025 ? 25000 : 0;
      holdings.add(new Holding(date, 25000 + deposit, 75000 + index * 33 + Math.sin(index / 15.0) * 1300, index * 1.7,
          100000 + deposit));
      index++;
    }
    flows.add(new Flow(LocalDate.of(2025, 7, 1), 25000));
    var period = period(WeekYear.WM_YEAR, holdings, flows, 0);
    assertEquals(period.getLastDayTotals().getTotalBalanceMC(), period.getFirstDayTotals().getTotalBalanceMC()
        + period.getDifference().getExternalCashTransferMC() + period.getDifference().getTotalGainMC(), .011);
    assertEquals(period.getMetrics().twrPercent(),
        (period.getReturnSeries().wealth(period.getReturnSeries().size() - 1) - 1) * 100, .011);
    Path dir = Path.of("target/performance-report-samples");
    Files.createDirectories(dir);
    for (String language : List.of("de", "en")) {
      for (var preset : List.of(PerformanceReportPreset.PERFORMANCE_DETAIL, PerformanceReportPreset.SHORT_REPORT)) {
        byte[] pdf = generate(period, request(language, preset));
        String name = preset.name().toLowerCase(Locale.ROOT) + "_" + language;
        Files.write(dir.resolve(name + ".pdf"), pdf);
        try (PDDocument document = Loader.loadPDF(pdf)) {
          String text = new PDFTextStripper().getText(document);
          assertTrue(text.contains("Müller"));
          assertTrue(text.contains("100’000.00"), text);
          assertTrue(text.contains(language.equals("de") ? "Seite 1 / " : "Page 1 / "));
          assertTrue(text.contains(language.equals("de") ? "Gesamtsumme" : "Grand total")
              || preset == PerformanceReportPreset.SHORT_REPORT, text);
          assertNotNull(document.getDocumentCatalog().getDocumentOutline().getFirstChild());
          if (preset == PerformanceReportPreset.SHORT_REPORT) {
            assertTrue(document.getNumberOfPages() <= 2);
          }
          if (preset == PerformanceReportPreset.PERFORMANCE_DETAIL) {
            assertEquals(6, document.getPage(0).getAnnotations().size());
          }
          PDFRenderer renderer = new PDFRenderer(document);
          for (int page = 0; page < document.getNumberOfPages(); page++) {
            ImageIO.write(renderer.renderImageWithDPI(page, 100), "png",
                dir.resolve(name + "_" + (page + 1) + ".png").toFile());
            ImageIO.write(renderer.renderImageWithDPI(page, 72, org.apache.pdfbox.rendering.ImageType.GRAY), "png",
                dir.resolve(name + "_gray_" + (page + 1) + ".png").toFile());
          }
        }
      }
    }
  }

  @Test
  @DisplayName("Deposits are not gains; zero capital and margin valuations retain their calculation meaning")
  void referenceCases() throws Exception {
    LocalDate date = LocalDate.of(2025, 3, 3);
    var deposit = period(WeekYear.WM_WEEK,
        List.of(new Holding(date, 100, 0, 0, 100), new Holding(date.plusDays(1), 160, 0, 0, 150)),
        List.of(new Flow(date.plusDays(1), 50)), 0);
    assertEquals(10, deposit.getDifference().getTotalGainMC());
    assertEquals(160, deposit.getReturnSeries().value(1));
    assertEquals(150, deposit.getReturnSeries().value(0) + deposit.getExternalCashTransfers().get(1)
        - deposit.getExternalCashTransfers().getFirst());
    var margin = period(WeekYear.WM_WEEK,
        List.of(new Holding(date, 100, 0, 0, 100), new Holding(date.plusDays(1), 100, 0, 12, 100)), List.of(), 2);
    assertEquals(112, margin.getReturnSeries().value(1));
    assertEquals(12, margin.getDifference().getTotalGainMC());
    assertEquals(2, margin.getMetrics().feesWithoutRate());
    assertNull(margin.getMetrics().feeRatioPercent());
    assertNotNull(deposit.getMetrics().feeRatioPercent());
    var empty = period(WeekYear.WM_WEEK,
        List.of(new Holding(date, 0, 0, 0, 0), new Holding(date.plusDays(1), 0, 0, 0, 0)), List.of(), 0);
    assertNull(empty.getMetrics().twrPercent());
    var request = request("en", PerformanceReportPreset.SHORT_REPORT);
    request.dateFrom = date;
    request.dateTo = date.plusDays(1);
    request.periodSplit = WeekYear.WM_WEEK;
    try (var pdf = Loader.loadPDF(generate(empty, request))) {
      assertTrue(new PDFTextStripper().getText(pdf).contains("No time-weighted return"));
    }
  }
}
