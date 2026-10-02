package grafioschtrader.report.pdf;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import grafiosch.common.ClientClock;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Tenant;
import grafioschtrader.reports.SecurityGroupByAssetclassWithCashReport;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.PortfolioJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;
import grafioschtrader.service.GlobalparametersService;

/** Real holdings grouping with explicit tenant and time; no database or request context is involved. */
class HoldingsScopeTest {
  @Test
  @DisplayName("A live price after the statement date cannot value a historical position without a historical quote")
  void noFuturePrice() {
    var repository = new grafioschtrader.repository.SecurityJpaRepositoryImpl();
    var history = mock(HistoryquoteJpaRepository.class);
    ReflectionTestUtils.setField(repository, "historyquoteJpaRepository", history);
    var p = HoldingsReportTest.position(1, "New quote", grafioschtrader.types.AssetclassType.EQUITIES, "CHF", 1000);
    p.getSecurity().setSLast(150.0);
    p.getSecurity().setSTimestamp(HoldingsReportTest.DATE.plusDays(1).atStartOfDay());
    repository.calcGainLossBasedOnDateOrNewestPrice(List.of(p), HoldingsReportTest.DATE);
    assertTrue(p.priceMissing);
    assertNull(p.closeDate);
    var historical = mock(grafioschtrader.dto.ISecuritycurrencyIdDateCloseCreateType.class);
    when(historical.getIdSecuritycurrency()).thenReturn(1);
    when(historical.getDate()).thenReturn(HoldingsReportTest.DATE.minusDays(2));
    when(historical.getClose()).thenReturn(90.0);
    when(history.getIdDateCloseByIdsAndDate(List.of(1), HoldingsReportTest.DATE)).thenReturn(List.of(historical));
    var priced = HoldingsReportTest.position(1, "Historical quote", grafioschtrader.types.AssetclassType.EQUITIES,
        "CHF", 1000);
    priced.getSecurity().setSLast(150.0);
    priced.getSecurity().setSTimestamp(HoldingsReportTest.DATE.plusDays(1).atStartOfDay());
    repository.calcGainLossBasedOnDateOrNewestPrice(List.of(priced), HoldingsReportTest.DATE);
    assertFalse(priced.priceMissing);
    assertEquals(90.0, priced.closePrice);
    assertEquals(HoldingsReportTest.DATE.minusDays(2), priced.closeDate);
  }

  @Test
  @DisplayName("Cash-only portfolios contain their own cash once, and tenant scope combines both in tenant currency")
  void portfolioIsolationAndWeekendRates() throws Exception {
    SecurityContextHolder.clearContext();
    var report = new SecurityGroupByAssetclassWithCashReport();
    var tenants = mock(TenantJpaRepository.class);
    var portfolios = mock(PortfolioJpaRepository.class);
    var history = mock(HistoryquoteJpaRepository.class);
    var pairs = mock(CurrencypairJpaRepository.class);
    var securities = mock(SecurityJpaRepository.class);
    var days = mock(TradingDaysPlusJpaRepository.class);
    var parameters = mock(GlobalparametersService.class);
    ReflectionTestUtils.setField(report, "tenantJpaRepository", tenants);
    ReflectionTestUtils.setField(report, "portfolioJpaRepository", portfolios);
    ReflectionTestUtils.setField(report, "historyquoteJpaRepository", history);
    ReflectionTestUtils.setField(report, "currencypairJpaRepository", pairs);
    ReflectionTestUtils.setField(report, "securityJpaRepository", securities);
    ReflectionTestUtils.setField(report, "tradingDaysPlusJpaRepository", days);
    ReflectionTestUtils.setField(report, "globalparametersService", parameters);
    when(parameters.getCurrencyPrecision()).thenReturn(Map.of("CHF", 2, "EUR", 2));
    when(parameters.getPrecisionForCurrency(anyString())).thenReturn(2);
    when(securities.processOpenPositionsWithActualPrice(any(), any())).thenAnswer(_ -> new ArrayList<>());
    Tenant tenant = mock(Tenant.class);
    when(tenant.getIdTenant()).thenReturn(7);
    when(tenant.getCurrency()).thenReturn("CHF");
    when(tenants.getReferenceById(7)).thenReturn(tenant);
    var chf = portfolio(11, "CHF", account(21, "CHF", 100));
    var eur = portfolio(12, "EUR", account(22, "EUR", 200));
    when(tenant.getPortfolioList()).thenReturn(List.of(chf, eur));
    when(portfolios.findByIdTenantAndIdPortfolio(7, 11)).thenReturn(chf);
    when(portfolios.findByIdTenantAndIdPortfolio(7, 12)).thenReturn(eur);
    Currencypair pair = new Currencypair();
    pair.setIdSecuritycurrency(50);
    pair.setFromCurrency("EUR");
    pair.setToCurrency("CHF");
    pair.setSLast(1.5); // Today's rate must never leak into a historical Sunday statement.
    when(pairs.getAllCurrencypairsByTenantInPortfolioAndAccounts(7)).thenReturn(List.of(pair));
    var quote = mock(grafioschtrader.dto.ISecuritycurrencyIdDateCloseCreateType.class);
    when(quote.getIdSecuritycurrency()).thenReturn(50);
    when(quote.getClose()).thenReturn(.9);
    when(history.getIdDateCloseByIdsAndDate(List.of(50), HoldingsReportTest.DATE)).thenReturn(List.of(quote));
    LocalDate date = HoldingsReportTest.DATE, today = date.plusDays(10);
    try (var clock = mockStatic(ClientClock.class)) {
      clock.when(ClientClock::today).thenThrow(new AssertionError("Implicit clock"));
      var first = report.getSecurityPositionGrandSummaryIdPortfolio(7, 11, false, date, today);
      var second = report.getSecurityPositionGrandSummaryIdPortfolio(7, 12, false, date, today);
      assertEquals(100, first.grandAccountValueSecurityMC);
      assertEquals("EUR", second.currency);
      assertEquals(200, second.grandAccountValueSecurityMC);
      var all = report.getSecurityPositionGrandSummaryIdTenant(7, false, date, today);
      assertEquals(280, all.grandAccountValueSecurityMC);
      assertEquals(280, new HoldingsData(all).total());
      assertEquals(2, new HoldingsData(all).positions().size());
      assertEquals(.9, all.exchangeRates.get("EUR"));
      verify(days, times(3)).hasTradingDayBetweenUntilYesterday(date, today);
      clock.verifyNoInteractions();
    }
    assertThrows(SecurityException.class,
        () -> report.getSecurityPositionGrandSummaryIdPortfolio(7, 99, false, date, today));
  }

  private Portfolio portfolio(int id, String currency, Cashaccount account) {
    var portfolio = mock(Portfolio.class);
    when(portfolio.getIdPortfolio()).thenReturn(id);
    when(portfolio.getCurrency()).thenReturn(currency);
    when(portfolio.getSecurityaccountList()).thenReturn(List.of());
    when(portfolio.getCashaccountList()).thenReturn(List.of(account));
    return portfolio;
  }

  private Cashaccount account(int id, String currency, double balance) {
    var account = mock(Cashaccount.class);
    when(account.getId()).thenReturn(id);
    when(account.getName()).thenReturn("Account " + id);
    when(account.getCurrency()).thenReturn(currency);
    when(account.calculateBalanceOnTransactions(HoldingsReportTest.DATE.plusDays(1).atStartOfDay()))
        .thenReturn(balance);
    return account;
  }
}
