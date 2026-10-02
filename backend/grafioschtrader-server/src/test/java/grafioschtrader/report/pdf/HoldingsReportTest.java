package grafioschtrader.report.pdf;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import grafiosch.repository.TaskDataChangeJpaRepository;
import grafioschtrader.dto.PerformanceReportRequest;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Tenant;
import grafioschtrader.reports.PerformanceReport;
import grafioschtrader.reports.SecurityGroupByAssetclassWithCashReport;
import grafioschtrader.reportviews.securityaccount.SecurityPositionGrandSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.repository.PortfolioJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.PerformanceReportPreset;
import grafioschtrader.types.PerformanceReportSection;
import grafioschtrader.types.SpecialInvestmentInstruments;

/** Fixed statement values independent of the renderer, including margin equity, missing prices and liabilities. */
class HoldingsReportTest {
  static final LocalDate DATE = LocalDate.of(2025, 12, 28);

  static SecurityPositionSummary position(int id, String name, AssetclassType type, String currency, double value) {
    var security = new Security();
    security.setIdSecuritycurrency(id);
    security.setName(name);
    security.setCurrency(currency);
    security.setIsin(id > 0 ? "CH1234567890" : null);
    var assetclass = new Assetclass();
    assetclass.setCategoryType(type);
    assetclass.setSpecialInvestmentInstrument(SpecialInvestmentInstruments.DIRECT_INVESTMENT);
    security.setAssetClass(assetclass);
    var p = new SecurityPositionSummary("CHF", security, Map.of("CHF", 2, "EUR", 2, "USD", 2));
    p.units = 10;
    p.closePrice = 100.0;
    p.closeDate = DATE.minusDays(2);
    p.accountValueSecurity = value;
    p.accountValueSecurityMC = value;
    p.valueSecurityMC = value;
    p.gainLossSecurityMC = 123.45;
    p.gainLossCurrencyMC = -12.34;
    return p;
  }

  static SecurityPositionGrandSummary summary(List<SecurityPositionSummary> positions) {
    var summary = new SecurityPositionGrandSummary("CHF", 2);
    var group = new grafioschtrader.reportviews.securityaccount.SecurityPositionDynamicGroupSummary<>(
        AssetclassType.EQUITIES);
    group.securityPositionSummaryList.addAll(positions);
    summary.securityPositionGroupSummaryList.add(group);
    summary.exchangeRates = Map.of("CHF", 1.0, "EUR", .94, "USD", .88);
    return summary;
  }

  static PerformanceReportRequest request(String language) {
    var request = PerformanceReportSampleWriterTest.request(language, PerformanceReportPreset.STATEMENT_OF_ASSETS);
    request.dateFrom = null;
    request.dateTo = null;
    request.periodSplit = null;
    request.reportDate = DATE;
    return request;
  }

  static byte[] generate(SecurityPositionGrandSummary summary, PerformanceReportRequest request) throws Exception {
    var performance = mock(PerformanceReport.class);
    var tenants = mock(TenantJpaRepository.class);
    var tenant = mock(Tenant.class);
    when(tenant.getTenantName()).thenReturn("Müller & Partner");
    when(tenant.getCurrency()).thenReturn("CHF");
    when(tenant.getPortfolioList()).thenReturn(List.of());
    when(tenants.findById(7)).thenReturn(java.util.Optional.of(tenant));
    var holdings = mock(SecurityGroupByAssetclassWithCashReport.class);
    when(holdings.getSecurityPositionGrandSummaryIdTenant(eq(7), eq(false), eq(DATE), any())).thenReturn(summary);
    var service = PerformanceReportSampleWriterTest.service(performance, tenants, mock(PortfolioJpaRepository.class),
        mock(TaskDataChangeJpaRepository.class));
    ReflectionTestUtils.setField(service, "holdings", holdings);
    byte[] result = service.generate(new ReportContext(7, null, Locale.forLanguageTag(request.language),
        Locale.forLanguageTag(request.numberFormat), DATE.plusDays(20), ZoneId.of("UTC")), request);
    verifyNoInteractions(performance);
    verify(holdings, times(1)).getSecurityPositionGrandSummaryIdTenant(7, false, DATE, DATE.plusDays(20));
    return result;
  }

  @Test
  @DisplayName("Margin exposure is excluded; liabilities retain their sign and non-positive totals have no weights")
  void signedWeights() {
    var cash = position(-1, "Cash", AssetclassType.CURRENCY_CASH, "CHF", 1000);
    var margin = position(1, "Margin", AssetclassType.EQUITIES, "USD", -100);
    margin.valueSecurityMC = 50000;
    margin.getSecurity().getAssetClass().setSpecialInvestmentInstrument(SpecialInvestmentInstruments.CFD);
    var data = new HoldingsData(summary(List.of(cash, margin)));
    assertEquals(900, data.total());
    assertEquals(-100, data.assetClasses().get(AssetclassType.EQUITIES));
    assertEquals(-11.111111, data.weight(-100), .000001);
    assertEquals(111.111111, data.weight(1000), .000001);
    assertEquals(100, data.weight(-100) + data.weight(1000), .000001);
    cash.accountValueSecurityMC = 50;
    assertNull(data.weight(50));
    margin.priceMissing = true;
    assertTrue(Double.isNaN(data.total()));
    assertNull(data.weight(50));
  }

  @Test
  @DisplayName("A combined period report values holdings at dateTo and explains the difference from performance")
  void combinedPeriod() throws Exception {
    var period = PerformanceReportSampleWriterTest.period(grafioschtrader.reportviews.performance.WeekYear.WM_YEAR,
        List.of(new PerformanceReportSampleWriterTest.Holding(PerformanceReportSampleWriterTest.FROM, 100, 0, 0, 100),
            new PerformanceReportSampleWriterTest.Holding(PerformanceReportSampleWriterTest.TO, 120, 0, 0, 100)),
        List.of(), 0);
    var request = PerformanceReportSampleWriterTest.request("en", PerformanceReportPreset.CUSTOM);
    request.sections = Set.of(PerformanceReportSection.PERFORMANCE_SUMMARY, PerformanceReportSection.HOLDINGS,
        PerformanceReportSection.ALLOCATION);
    // reportDate cannot override the end date of a period report.
    request.reportDate = DATE.minusYears(1);
    var summary = summary(List.of(position(-1, "Cash", AssetclassType.CURRENCY_CASH, "CHF", 130)));
    try (var pdf = Loader.loadPDF(PerformanceReportSampleWriterTest.generate(period, request, summary))) {
      String text = new PDFTextStripper().getText(pdf);
      assertTrue(text.contains("Difference from performance closing value: 10.00 CHF"));
      assertTrue(text.contains("31.12.2025"));
      assertFalse(text.contains("28.12.2024"));
      assertTrue(text.contains("Development of assets"));
    }
  }

  @Test
  @DisplayName("Cash-only reports work on a Sunday without a performance calculation, and sections are optional")
  void cashOnlyAndSelection() throws Exception {
    var summary = summary(List.of(position(-1, "Cash only", AssetclassType.CURRENCY_CASH, "CHF", 1234.56)));
    var request = request("en");
    request.preset = PerformanceReportPreset.CUSTOM;
    request.sections = Set.of(PerformanceReportSection.HOLDINGS);
    request.comment = "Quarterly review";
    try (var pdf = Loader.loadPDF(generate(summary, request))) {
      String text = new PDFTextStripper().getText(pdf);
      assertTrue(text.contains("Quarterly review"));
      assertTrue(text.contains("1’234.56"));
      assertTrue(text.contains("Reporting date"));
      assertFalse(text.contains("Excluded valuation base"));
      assertFalse(text.contains("Currency × asset class"));
    }
    request.sections = Set.of(PerformanceReportSection.ALLOCATION);
    try (var pdf = Loader.loadPDF(generate(summary, request))) {
      String text = new PDFTextStripper().getText(pdf);
      assertTrue(text.contains("1’234.56"));
      assertFalse(text.contains("Cash only"));
    }
  }

  @Test
  @DisplayName("Tiny allocations are merged only in the chart, using the unrounded half-percent threshold")
  void chartOther() {
    var slices = AllocationRenderer.slices(Map.of("Large", 995.01, "Small", 4.99), "Other");
    assertTrue(slices.stream().anyMatch(s -> s.label().equals("Other") && s.value() == 4.99));
    assertFalse(AllocationRenderer.slices(Map.of("Large", 995.0, "Small", 5.0), "Other").stream()
        .anyMatch(s -> s.label().equals("Other")));
  }

  @Test
  @DisplayName("DE/EN statements retain long names, complete identifiers, subtotals, details and all pages")
  void statementSamples() throws Exception {
    for (String language : List.of("en", "de")) {
      for (boolean longReport : List.of(false, true)) {
        List<SecurityPositionSummary> positions = new ArrayList<>();
        int count = longReport ? 45 : 2;
        for (int i = 0; i < count; i++) {
          var p = position(i + 1, "Global diversified investment fund with a long share class name " + i,
              i % 2 == 0 ? AssetclassType.EQUITIES : AssetclassType.FIXED_INCOME, "EUR", 1000);
          positions.add(p);
        }
        var margin = position(100, "Margin position", AssetclassType.COMMODITIES, "USD", longReport ? -100 : 100);
        margin.getSecurity().getAssetClass().setSpecialInvestmentInstrument(SpecialInvestmentInstruments.CFD);
        margin.valueSecurityMC = 50000;
        positions.add(margin);
        positions.add(position(-1, "Cash account", AssetclassType.CURRENCY_CASH, "CHF", 500));
        var request = request(language);
        request.detailColumns = longReport;
        byte[] bytes = generate(summary(positions), request);
        String name = "statement_" + language + (longReport ? "_long" : "_compact");
        saveSample(name, bytes);
        try (var pdf = Loader.loadPDF(bytes)) {
          String text = new PDFTextStripper().getText(pdf);
          assertTrue(text.contains("CH1234567890"));
          assertTrue(text.contains(longReport ? "45’400.00" : "2’600.00"));
          assertTrue(text.contains("50’000.00"));
          assertTrue(text.contains(language.equals("de") ? "Älterer Kurs" : "Older price"));
          assertEquals(3, pdf.getPage(0).getAnnotations().size());
          assertTrue(text.contains(
              (language.equals("de") ? "Seite " : "Page ") + pdf.getNumberOfPages() + " / " + pdf.getNumberOfPages()));
          if (longReport) {
            assertTrue(text.contains("123.45"));
          }
        }
      }
    }
  }

  static void saveSample(String name, byte[] bytes) throws Exception {
    Path dir = Path.of("target/performance-report-samples");
    Files.createDirectories(dir);
    Files.write(dir.resolve(name + ".pdf"), bytes);
    try (var pdf = Loader.loadPDF(bytes)) {
      var renderer = new PDFRenderer(pdf);
      for (int page = 0; page < pdf.getNumberOfPages(); page++) {
        ImageIO.write(renderer.renderImageWithDPI(page, 90), "png",
            dir.resolve(name + "_" + (page + 1) + ".png").toFile());
        ImageIO.write(renderer.renderImageWithDPI(page, 72, ImageType.GRAY), "png",
            dir.resolve(name + "_gray_" + (page + 1) + ".png").toFile());
      }
    }
  }

  @Test
  @DisplayName("Missing prices stay visible and invalidate affected statement totals instead of becoming zero")
  void missingPrice() throws Exception {
    var p = position(1, "Unpriced position", AssetclassType.EQUITIES, "CHF", 0);
    p.priceMissing = true;
    try (var pdf = Loader.loadPDF(generate(summary(List.of(p)), request("en")))) {
      String text = new PDFTextStripper().getText(pdf);
      assertTrue(text.contains("Missing price"));
      assertTrue(text.contains("n/a"));
      assertFalse(text.contains("100.00 %"));
    }
  }
}
