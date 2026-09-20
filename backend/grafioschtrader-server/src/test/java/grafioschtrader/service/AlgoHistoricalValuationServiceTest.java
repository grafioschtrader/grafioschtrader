package grafioschtrader.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import grafioschtrader.dto.ISecuritycurrencyIdDateCloseCreateType;
import grafioschtrader.entities.*;
import grafioschtrader.repository.*;
import grafioschtrader.types.*;

class AlgoHistoricalValuationServiceTest {
  @Test
  void initializationModeHasStringMetadataForTheGeneratedSelector() {
    var descriptor = new grafiosch.dynamic.model.FieldDescriptorInputAndShow("initializationMode",
        SimulationInitializationMode.class);
    assertThat(descriptor.dataType).isEqualTo(grafiosch.dynamic.model.DataType.String);
    assertThat(descriptor.enumType).isEqualTo("SimulationInitializationMode");
  }

  private final LocalDate date = LocalDate.of(2020, 6, 15);
  private final AlgoHistoricalValuationService service = new AlgoHistoricalValuationService();
  private final SimulationSourceRepository source = mock(SimulationSourceRepository.class);
  private final HoldSecurityaccountSecurityJpaRepository holdings = mock(
      HoldSecurityaccountSecurityJpaRepository.class);
  private final HoldCashaccountBalanceJpaRepository balances = mock(HoldCashaccountBalanceJpaRepository.class);
  private final HistoryquoteJpaRepository quotes = mock(HistoryquoteJpaRepository.class);
  private final CurrencypairJpaRepository currencies = mock(CurrencypairJpaRepository.class);
  private final SecuritysplitJpaRepository splits = mock(SecuritysplitJpaRepository.class);
  private final Cashaccount cash = cash(3, "CHF");

  @BeforeEach
  void setup() {
    ReflectionTestUtils.setField(service, "source", source);
    ReflectionTestUtils.setField(service, "holdings", holdings);
    ReflectionTestUtils.setField(service, "balances", balances);
    ReflectionTestUtils.setField(service, "quotes", quotes);
    ReflectionTestUtils.setField(service, "currencies", currencies);
    ReflectionTestUtils.setField(service, "splits", splits);
    TenantJpaRepository tenants = mock(TenantJpaRepository.class);
    Tenant tenant = new Tenant("test", "CHF", 1, TenantKindType.MAIN, false);
    when(tenants.findById(1)).thenReturn(Optional.of(tenant));
    ReflectionTestUtils.setField(service, "tenants", tenants);
    when(source.cashaccounts(1)).thenReturn(List.of(cash));
    when(source.transactions(1, date.plusDays(1))).thenReturn(List.of());
    when(holdings.findOpenPositionsAtDate(1, date)).thenReturn(List.of());
    when(balances.findCashBalancesAtDate(1, date)).thenReturn(List.of());
    when(splits.getSecuritysplitMapByIdSecuritycurrency(anyInt())).thenReturn(Map.of());
  }

  @Test
  void opposingAccountsPreserveGrossExposureAndSignedEquity() {
    Security security = mock(Security.class);
    when(security.getId()).thenReturn(5);
    when(security.getIdSecuritycurrency()).thenReturn(5);
    when(security.getName()).thenReturn("Test share");
    when(security.getCurrency()).thenReturn("CHF");
    Transaction buy = trade(security, 10, 10, TransactionType.ACCUMULATE);
    Transaction shortSale = trade(security, 11, 4, TransactionType.REDUCE);
    when(source.transactions(1, date.plusDays(1))).thenReturn(List.of(buy, shortSale));
    when(holdings.findOpenPositionsAtDate(1, date)).thenReturn(List.of(holding(10, 5, 10), holding(11, 5, -4)));
    price(5, 100);
    var result = service.value(1, date);
    assertThat(result.errors()).isEmpty();
    assertThat(result.positions()).hasSize(2);
    assertThat(result.equity()).isEqualTo(600);
    assertThat(result.grossExposure()).isEqualTo(1400);
  }

  @Test
  void missingQuoteIsAnErrorInsteadOfOmittingTheInstrumentSilently() {
    Security security = mock(Security.class);
    when(security.getId()).thenReturn(5);
    when(security.getName()).thenReturn("Missing share");
    Transaction buy = trade(security, 10, 10, TransactionType.ACCUMULATE);
    when(source.transactions(1, date.plusDays(1))).thenReturn(List.of(buy));
    when(holdings.findOpenPositionsAtDate(1, date)).thenReturn(List.of(holding(10, 5, 10)));
    var result = service.value(1, date);
    assertThat(result.errors()).containsExactly("Missing share");
    assertThatThrownBy(result::requireAvailable).isInstanceOf(grafiosch.exceptions.DataViolationException.class);
  }

  @Test
  void currencyPresentOnlyInCashRequiresHistoricalFx() {
    Cashaccount usd = cash(4, "USD");
    when(source.cashaccounts(1)).thenReturn(List.of(cash, usd));
    var result = service.value(1, date);
    assertThat(result.errors()).containsExactly("USD/CHF");
    Currencypair pair = mock(Currencypair.class);
    when(pair.getId()).thenReturn(9);
    when(currencies.findByFromCurrencyAndToCurrency("USD", "CHF")).thenReturn(pair);
    price(9, 0.9);
    result = service.value(1, date);
    assertThat(result.errors()).isEmpty();
    assertThat(result.fx()).containsEntry("USD", 0.9);
  }

  @Test
  void suppliedMarketNeverFallsBackToHistoricalQuotes() {
    Cashaccount usd = cash(4, "USD");
    when(source.cashaccounts(1)).thenReturn(List.of(cash, usd));
    Currencypair pair = mock(Currencypair.class);
    when(pair.getId()).thenReturn(9);
    when(currencies.findByFromCurrencyAndToCurrency("USD", "CHF")).thenReturn(pair);
    assertThat(service.value(1, date, (_, _) -> null).errors()).containsExactly("USD/CHF");
    assertThat(service.value(1, date, (_, _) -> 0.8).fx()).containsEntry("USD", 0.8);
    verifyNoInteractions(quotes);
  }

  @Test
  void allocationsUseEquityAndNormalizeAtEachSiblingLevel() {
    assertThat(AlgoAllocationWeights.topPercentage(100000, 48200)).isEqualTo(48.2);
    assertThat(AlgoAllocationWeights.normalize(Map.of(1, 24100.0, 2, 14460.0, 3, 9640.0))).containsEntry(1, 50f)
        .containsEntry(2, 30f).containsEntry(3, 20f);
    assertThat(AlgoAllocationWeights.normalize(Map.of(1, 1.0, 2, 1.0, 3, 1.0))).containsEntry(1, 33.34f)
        .containsEntry(2, 33.33f).containsEntry(3, 33.33f);
    assertThatThrownBy(() -> AlgoAllocationWeights.topPercentage(0, 100)).isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> AlgoAllocationWeights.topPercentage(100, 101)).isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> AlgoHistoricalValuationService.validateDate(LocalDate.now()))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void opposingMarginContractsUseSettlementForEquityAndBothNotionalsForExposure() {
    Assetclass asset = new Assetclass();
    asset.setCategoryType(AssetclassType.EQUITIES);
    asset.setSpecialInvestmentInstrument(SpecialInvestmentInstruments.CFD);
    Security security = new Security();
    security.setIdSecuritycurrency(5);
    security.setName("Margin instrument");
    security.setCurrency("CHF");
    security.setAssetClass(asset);
    Transaction longOpen = trade(security, 10, 2, TransactionType.ACCUMULATE);
    Transaction shortOpen = trade(security, 10, 2, TransactionType.REDUCE);
    shortOpen.setIdTransaction(11);
    for (Transaction tx : List.of(longOpen, shortOpen)) {
      tx.setQuotation(100.0);
      tx.setAssetInvestmentValue2(10.0);
      tx.setCashaccountAmount(0.0);
    }
    when(source.transactions(1, date.plusDays(1))).thenReturn(List.of(longOpen, shortOpen));
    var calculator = new grafioschtrader.instrument.SecurityCalcService();
    ReflectionTestUtils.setField(service, "calculator", calculator);
    var parameters = mock(GlobalparametersService.class);
    when(parameters.getCurrencyPrecision()).thenReturn(Map.of("CHF", 2));
    ReflectionTestUtils.setField(service, "parameters", parameters);
    price(5, 110);
    var result = service.value(1, date);
    assertThat(result.positions()).hasSize(2);
    assertThat(result.positions()).extracting(AlgoHistoricalValuationService.Position::closingValue)
        .containsExactlyInAnyOrder(200.0, -200.0);
    assertThat(result.equity()).isZero();
    assertThat(result.grossExposure()).isEqualTo(4400);

    var batch = service.valueSeries(1, List.of(date), (_, _) -> 110.0, Map.of(5, security)).get(date);
    assertThat(batch.equity()).isEqualTo(result.equity());
    assertThat(batch.grossExposure()).isEqualTo(result.grossExposure());
  }

  @Test
  void batchMatchesDailyValuationAcrossSplitSaleCashChangesAndInverseFx() {
    Security security = mock(Security.class);
    when(security.getId()).thenReturn(5);
    when(security.getName()).thenReturn("Foreign share");
    when(security.getCurrency()).thenReturn("USD");
    Currencypair inverse = mock(Currencypair.class);
    when(inverse.getId()).thenReturn(9);
    when(currencies.findByFromCurrencyAndToCurrency("CHF", "USD")).thenReturn(inverse);
    var beforeSplit = holding(10, 5, 10);
    beforeSplit.setToHoldDate(date);
    var afterSplit = new HoldSecurityaccountSecurity(1, 2, 10, 5, date.plusDays(1), 20, null, null, 0.5, null, null);
    afterSplit.setToHoldDate(date.plusDays(1));
    var cashBefore = cashPeriod(date, 100);
    cashBefore.setToHoldDate(date.plusDays(1));
    var cashAfter = cashPeriod(date.plusDays(2), 700);
    List<LocalDate> dates = List.of(date, date.plusDays(1), date.plusDays(2), date.plusDays(3));
    var market = new AlgoHistoricalValuationService.ClosingPrices() {
      public Double close(Integer id, LocalDate asOf) {
        return id == 9 ? 2.0 : 100.0 + 10 * (asOf.toEpochDay() - date.toEpochDay());
      }

      public Map<String, Double> receivables(LocalDate asOf) {
        return asOf.equals(date.plusDays(1)) ? Map.of("USD", 20.0) : Map.of();
      }
    };
    Map<LocalDate, AlgoHistoricalValuationService.Snapshot> daily = new LinkedHashMap<>();
    for (LocalDate day : dates) {
      List<HoldSecurityaccountSecurity> active = day.equals(date) ? List.of(beforeSplit)
          : day.equals(date.plusDays(1)) ? List.of(afterSplit) : List.of();
      when(holdings.findOpenPositionsAtDate(1, day)).thenReturn(active);
      when(balances.findCashBalancesAtDate(1, day))
          .thenReturn(List.of(day.isBefore(date.plusDays(2)) ? cashBefore : cashAfter));
      when(source.transactions(1, day.plusDays(1)))
          .thenReturn(List.of(trade(security, 10, 10, TransactionType.ACCUMULATE)));
      daily.put(day, service.value(1, day, market));
    }
    when(holdings.findPositionsInPeriod(1, date, dates.getLast())).thenReturn(List.of(afterSplit, beforeSplit));
    when(balances.findBalancesInPeriod(1, date, dates.getLast())).thenReturn(List.of(cashAfter, cashBefore));
    clearInvocations(new Object[] { source, holdings, balances, currencies, splits });

    var batch = service.valueSeries(1, dates.reversed(), market, Map.of(5, security));
    assertThat(batch.keySet()).containsExactlyElementsOf(dates);
    batch.forEach((day, actual) -> {
      assertThat(actual.equity()).isCloseTo(daily.get(day).equity(), within(1e-9));
      assertThat(actual.grossExposure()).isCloseTo(daily.get(day).grossExposure(), within(1e-9));
      assertThat(actual.fx()).isEqualTo(daily.get(day).fx());
      assertThat(actual.errors()).isEqualTo(daily.get(day).errors());
    });
    assertThat(batch.values()).extracting(AlgoHistoricalValuationService.Snapshot::equity).containsExactly(600.0, 660.0,
        700.0, 700.0);
    verify(source).cashaccounts(1);
    verifyNoMoreInteractions(source);
    verify(holdings).findPositionsInPeriod(1, date, dates.getLast());
    verify(balances).findBalancesInPeriod(1, date, dates.getLast());
    verifyNoMoreInteractions(holdings, balances);
    verify(currencies).findByFromCurrencyAndToCurrency("USD", "CHF");
    verify(currencies).findByFromCurrencyAndToCurrency("CHF", "USD");
    verifyNoMoreInteractions(currencies);
    verifyNoInteractions(splits, quotes);
  }

  @Test
  void seventeenHundredDaysUseTwoPeriodReadsAndNoLedgerReads() {
    var dates = date.datesUntil(date.plusDays(1711)).toList();
    var held = holding(10, 5, 10);
    Security security = mock(Security.class);
    when(security.getId()).thenReturn(5);
    when(security.getCurrency()).thenReturn("CHF");
    when(security.getName()).thenReturn("Missing share");
    when(holdings.findPositionsInPeriod(1, date, dates.getLast())).thenReturn(List.of(held));
    when(balances.findBalancesInPeriod(1, date, dates.getLast())).thenReturn(List.of(cashPeriod(date, 100)));
    var batch = service.valueSeries(1, dates, (_, day) -> day.equals(date) ? null : 100.0, Map.of(5, security));
    assertThat(batch).hasSize(1711);
    assertThat(batch.get(date).errors()).containsExactly("Missing share");
    assertThat(batch.get(dates.getLast()).equity()).isEqualTo(1100);
    verify(source).cashaccounts(1);
    verifyNoMoreInteractions(source);
    verify(holdings).findPositionsInPeriod(1, date, dates.getLast());
    verify(balances).findBalancesInPeriod(1, date, dates.getLast());
    verifyNoMoreInteractions(holdings, balances);
  }

  private HoldCashaccountBalance cashPeriod(LocalDate from, double balance) {
    return new HoldCashaccountBalance(1, 2, 3, from, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, balance, null, null);
  }

  private Cashaccount cash(int id, String currency) {
    Cashaccount account = new Cashaccount();
    account.setIdSecuritycashAccount(id);
    account.setCurrency(currency);
    return account;
  }

  private Transaction trade(Security security, int account, double units, TransactionType type) {
    Transaction tx = new Transaction();
    tx.setIdTransaction(account);
    tx.setSecuritycurrency(security);
    tx.setCashaccount(cash);
    tx.setIdSecurityaccount(account);
    tx.setTransactionTime(date.atTime(12, 0));
    tx.onPrePersist();
    tx.setUnits(units);
    tx.setTransactionType(type);
    return tx;
  }

  private HoldSecurityaccountSecurity holding(int account, int security, double units) {
    return new HoldSecurityaccountSecurity(1, 2, account, security, date, units, null, null, 1, null, null);
  }

  private void price(int id, double value) {
    ISecuritycurrencyIdDateCloseCreateType quote = mock(ISecuritycurrencyIdDateCloseCreateType.class);
    when(quote.getClose()).thenReturn(value);
    when(quotes.getIdDateCloseByIdsAndDate(List.of(id), date)).thenReturn(List.of(quote));
  }
}
