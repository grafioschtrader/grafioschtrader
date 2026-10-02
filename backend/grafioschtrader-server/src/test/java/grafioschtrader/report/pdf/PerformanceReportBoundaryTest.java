package grafioschtrader.report.pdf;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import grafiosch.common.ClientClock;
import grafiosch.entities.TaskDataChange;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.security.filter.TenantReadOnlyFilter;
import grafiosch.types.ProgressStateType;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reports.PerformanceReport;
import grafioschtrader.reportviews.performance.WeekYear;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.HoldCashaccountDepositJpaRepository;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository;
import grafioschtrader.repository.PortfolioJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;
import grafioschtrader.security.filter.SecurityGTConfig;
import grafioschtrader.types.PerformanceReportPreset;
import grafioschtrader.types.PerformanceReportSection;
import jakarta.validation.Validation;

/** Guards request boundaries and the context-free calculation path without starting Spring or a database. */
class PerformanceReportBoundaryTest {
  @Test
  @DisplayName("Booking loaders retain tenant, portfolio, business-date bounds and explicit calendar without a request")
  void bookingScopeAndBounds() {
    var transactions = mock(TransactionJpaRepository.class);
    var quotes = mock(HistoryquoteJpaRepository.class);
    var currencies = mock(CurrencypairJpaRepository.class);
    var tradingDays = mock(TradingDaysPlusJpaRepository.class);
    var loader = new BookingDataLoader(transactions, quotes, currencies, tradingDays);
    LocalDate from = LocalDate.of(2022, 12, 30), to = LocalDate.of(2023, 1, 3), today = to.plusDays(20);
    var weekendFee = BookingDataTest.booking(1, grafioschtrader.types.TransactionType.FEE, null, "EUR", -10);
    weekendFee.setTransactionTime(LocalDate.of(2022, 12, 31).atTime(15, 0));
    ReflectionTestUtils.setField(weekendFee, "transactionDate", LocalDate.of(2022, 12, 31));
    when(transactions.findByIdTenantAndIdPortfolioAndTransactionDateBetweenForReport(7, from, to, 99))
        .thenReturn(List.of(weekendFee));
    var context = new ReportContext(7, 99, Locale.ENGLISH, Locale.forLanguageTag("de-CH"), today, ZoneId.of("UTC"));
    SecurityContextHolder.clearContext();
    try (var clock = mockStatic(ClientClock.class)) {
      clock.when(ClientClock::today).thenThrow(new AssertionError("Request clock leaked into booking loader"));
      var result = loader.load(context, from, to, "EUR", true, false, null, null);
      assertEquals(10, result.fees());
      assertEquals(LocalDate.of(2022, 12, 31), result.transactions().getFirst().transaction().getTransactionDate());
      verify(transactions).findByIdTenantAndIdPortfolioAndTransactionDateBetweenForReport(7, from, to, 99);
      verify(transactions, never()).findByIdTenantAndTransactionDateBetweenForReport(anyInt(), any(), any());
      verify(quotes).getHistoryquotesForAllForeignTransactionsByIdPortfolio(99);
      verify(currencies).getAllCurrencypairsForPortfolioByPortfolio(99);
      verify(tradingDays).hasTradingDayBetweenUntilYesterday(to, today);
      clock.verifyNoInteractions();
    }
    var tenantContext = new ReportContext(7, null, context.language(), context.numberFormat(), today, context.zone());
    when(transactions.findByIdTenantAndTransactionDateBetweenForReport(7, from, to)).thenReturn(List.of(weekendFee));
    when(quotes.getHistoryquotesForAllForeignTransactionsByIdTenant(7))
        .thenReturn(java.util.Collections.singletonList(new Object[] { LocalDate.of(2022, 12, 31), "EUR", .95 }));
    assertEquals(9.5, loader.load(tenantContext, from, to, "CHF", false, false, null, null).fees());
    verify(transactions).findByIdTenantAndTransactionDateBetweenForReport(7, from, to);
  }

  @Test
  @DisplayName("The inherited report form includes usable calendar-year and detail-column inputs")
  void reportFormDefinition() {
    var fields = grafiosch.dynamic.model.DynamicModelHelper.getFormDefinitionOfEntityClass(
        grafioschtrader.dto.PerformanceReportRequest.class, 1).fieldDescriptorInputAndShows;
    var years = fields.stream().filter(f -> f.fieldName.equals("annualYears")).findFirst().orElseThrow();
    assertEquals(grafiosch.dynamic.model.DataType.NumericInteger, years.dataType);
    assertEquals(1.0, years.min);
    assertEquals(20.0, years.max);
    assertEquals(grafiosch.dynamic.model.DataType.Boolean,
        fields.stream().filter(f -> f.fieldName.equals("detailColumns")).findFirst().orElseThrow().dataType);
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Explicit context reaches trading-day and fee loaders without reading ClientClock or SecurityContext")
  void callerIndependentCalculation() throws Exception {
    var report = spy(new PerformanceReport());
    var holdings = mock(HoldSecurityaccountSecurityJpaRepository.class);
    var tenants = mock(TenantJpaRepository.class);
    var transactions = mock(TransactionJpaRepository.class);
    var tradingDays = mock(TradingDaysPlusJpaRepository.class);
    ReflectionTestUtils.setField(report, "holdSecurityaccountSecurityRepository", holdings);
    ReflectionTestUtils.setField(report, "tenantJpaRepository", tenants);
    ReflectionTestUtils.setField(report, "portfolioJpaRepository", mock(PortfolioJpaRepository.class));
    ReflectionTestUtils.setField(report, "holdCashaccountDepositJpaRepository",
        mock(HoldCashaccountDepositJpaRepository.class));
    ReflectionTestUtils.setField(report, "transactionJpaRepository", transactions);
    ReflectionTestUtils.setField(report, "historyquoteJpaRepository", mock(HistoryquoteJpaRepository.class));
    ReflectionTestUtils.setField(report, "currencypairJpaRepository", mock(CurrencypairJpaRepository.class));
    ReflectionTestUtils.setField(report, "tradingDaysPlusJpaRepository", tradingDays);
    LocalDate from = LocalDate.of(2025, 3, 3), to = from.plusDays(1), today = to.plusDays(1);
    doReturn(PerformanceReportSampleWriterTest.days(from, to)).when(report).getFirstAndMissingTradingDaysByTenant(7,
        today);
    Tenant tenant = new Tenant();
    tenant.setCurrency("CHF");
    when(tenants.getReferenceById(7)).thenReturn(tenant);
    when(holdings.getPeriodHoldingsByTenant(7, from, to))
        .thenReturn(List.of(new PerformanceReportSampleWriterTest.Holding(from, 100, 0, 0, 100),
            new PerformanceReportSampleWriterTest.Holding(to, 160, 0, 0, 150)));
    Transaction known = mock(Transaction.class), missing = mock(Transaction.class);
    when(known.getExchangeRateOnCurrencyOrNull(eq("CHF"), any())).thenReturn(.9);
    when(known.getFeeMC(any())).thenReturn(9.0);
    when(missing.getExchangeRateOnCurrencyOrNull(eq("CHF"), any())).thenReturn(null);
    when(transactions.findFeesByIdTenantBetween(7, from, to)).thenReturn(List.of(known, missing));
    SecurityContextHolder.clearContext();
    try (var clock = mockStatic(ClientClock.class)) {
      clock.when(ClientClock::today).thenThrow(new AssertionError("Request clock leaked into explicit context"));
      var result = report.getPeriodPerformanceByTenant(7, "de-CH", today, from, to, WeekYear.WM_WEEK);
      assertEquals(10, result.getDifference().getTotalGainMC());
      assertEquals(9, result.getMetrics().feesMC());
      assertEquals(1, result.getMetrics().feesWithoutRate());
      verify(tradingDays).hasTradingDayBetweenUntilYesterday(to, today);
      clock.verifyNoInteractions();
    }
  }

  @Test
  @DisplayName("Booking sections are available; decorative-only documents and overlong recipients fail before loading")
  void validation() {
    var validation = new PerformanceReportValidation(Validation.buildDefaultValidatorFactory().getValidator());
    var request = PerformanceReportSampleWriterTest.request("en", PerformanceReportPreset.SHORT_REPORT);
    validation.settings(request);
    // A named preset stands for exactly its sections; a different selection is a custom one.
    request.sections = Set.of(PerformanceReportSection.TRANSACTIONS);
    assertThrows(DataViolationException.class, () -> validation.settings(request));
    request.preset = PerformanceReportPreset.CUSTOM;
    validation.settings(request);
    request.sections = PerformanceReportPreset.INCOME_AND_COSTS.getSections();
    validation.settings(request);
    request.sections = Set.of(PerformanceReportSection.COVER, PerformanceReportSection.GLOSSARY);
    assertThrows(DataViolationException.class, () -> validation.settings(request));
    request.sections = PerformanceReportPreset.SHORT_REPORT.getSections();
    request.recipient = "x".repeat(46);
    assertThrows(DataViolationException.class, () -> validation.settings(request));
    request.recipient = "x\n".repeat(6) + "x";
    assertThrows(DataViolationException.class, () -> validation.settings(request));
  }

  @Test
  @DisplayName("Date sections allow today and weekends; period sections still require an excluded valuation base")
  void reportingDateBoundary() {
    var validation = new PerformanceReportValidation(Validation.buildDefaultValidatorFactory().getValidator());
    var request = HoldingsReportTest.request("en");
    var context = new ReportContext(7, null, Locale.ENGLISH, Locale.forLanguageTag("de-CH"), request.reportDate,
        ZoneId.of("UTC"));
    validation.request(context, request);
    request.reportDate = context.today().plusDays(1);
    assertThrows(DataViolationException.class, () -> validation.request(context, request));
    request.reportDate = context.today();
    request.preset = PerformanceReportPreset.CUSTOM;
    request.sections = Set.of(PerformanceReportSection.HOLDINGS, PerformanceReportSection.PERFORMANCE_SUMMARY);
    assertThrows(DataViolationException.class, () -> validation.request(context, request));
    request.sections = Set.of(PerformanceReportSection.HOLDINGS);
    var service = PerformanceReportSampleWriterTest.service(mock(PerformanceReport.class),
        mock(TenantJpaRepository.class), mock(PortfolioJpaRepository.class), mock(TaskDataChangeJpaRepository.class));
    assertEquals("statement_20251228.pdf", service.filename(context, request));
    // Date sections chosen in the period dialog remain a statement of assets on the end of the displayed period.
    request.reportDate = null;
    request.dateFrom = LocalDate.of(2025, 11, 28);
    request.dateTo = LocalDate.of(2025, 12, 26);
    request.periodSplit = WeekYear.WM_WEEK;
    assertFalse(request.hasPeriod());
    validation.request(context, request);
    assertEquals("statement_20251226.pdf", service.filename(context, request));
    assertTrue(service.options().dateSections().contains(PerformanceReportSection.ALLOCATION));
    assertFalse(service.options().dateSections().contains(PerformanceReportSection.PERFORMANCE_SUMMARY));
    assertFalse(service.options().dateSections().contains(PerformanceReportSection.TRANSACTIONS));
    assertFalse(service.options().dateSections().contains(PerformanceReportSection.INCOME_COSTS));
    assertTrue(service.options().presetSections().containsKey(PerformanceReportPreset.INCOME_AND_COSTS));
  }

  @Test
  @DisplayName("Foreign portfolios and both tenant and global pending rebuilds are rejected before calculation")
  void scopeAndRebuild() throws Exception {
    var performance = mock(PerformanceReport.class);
    var tenants = mock(TenantJpaRepository.class);
    when(tenants.findById(7)).thenReturn(java.util.Optional.of(new Tenant()));
    var tasks = mock(TaskDataChangeJpaRepository.class);
    var service = PerformanceReportSampleWriterTest.service(performance, tenants, mock(PortfolioJpaRepository.class),
        tasks);
    var request = PerformanceReportSampleWriterTest.request("en", PerformanceReportPreset.SHORT_REPORT);
    request.idPortfolio = 99;
    var foreign = new ReportContext(7, 99, Locale.ENGLISH, Locale.forLanguageTag("de-CH"), LocalDate.of(2026, 1, 15),
        ZoneId.of("UTC"));
    assertThrows(SecurityException.class, () -> service.generate(foreign, request));
    request.idPortfolio = null;
    var context = new ReportContext(7, null, foreign.language(), foreign.numberFormat(), foreign.today(),
        foreign.zone());
    for (Integer id : new Integer[] { 7, null }) {
      TaskDataChange task = mock(TaskDataChange.class);
      when(task.getIdEntity()).thenReturn(id);
      when(task.getProgressStateType()).thenReturn(ProgressStateType.PROG_WAITING);
      when(tasks.findByIdTaskIn(anyList())).thenReturn(List.of(task));
      assertThrows(DataViolationException.class, () -> service.generate(context, request));
      when(task.getProgressStateType()).thenReturn(ProgressStateType.PROG_RUNNING);
      assertThrows(DataViolationException.class, () -> service.generate(context, request));
    }
    verifyNoInteractions(performance);
  }

  @Test
  @DisplayName("Read-only clients may generate PDFs but cannot persist report settings")
  void readOnlyBoundary() throws Exception {
    User user = new User();
    user.setTenantAccessReadOnly(true);
    user.setLocaleStr("de-CH");
    var auth = new UsernamePasswordAuthenticationToken("client", "unused");
    auth.setDetails(user);
    SecurityContextHolder.getContext().setAuthentication(auth);
    var filter = new TenantReadOnlyFilter(PerformanceReportSampleWriterTest.messages(),
        SecurityGTConfig.READ_ONLY_WRITE_ALLOW_LIST);
    var pdf = new MockHttpServletRequest("POST", "/api/holding/report/pdf");
    pdf.setServletPath(pdf.getRequestURI());
    var called = new java.util.concurrent.atomic.AtomicBoolean();
    filter.doFilter(pdf, new MockHttpServletResponse(), (_, _) -> called.set(true));
    assertTrue(called.get());
    var settings = new MockHttpServletRequest("PATCH", "/api/tenant/reportsettings");
    settings.setServletPath(settings.getRequestURI());
    var response = new MockHttpServletResponse();
    called.set(false);
    filter.doFilter(settings, response, (_, _) -> called.set(true));
    assertFalse(called.get());
    assertEquals(403, response.getStatus());
  }
}
