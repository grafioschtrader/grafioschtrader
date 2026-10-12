package grafioschtrader.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import grafiosch.entities.User;
import grafioschtrader.dto.FxObservationGroup;
import grafioschtrader.dto.FxOutcome;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Transaction;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.SecurityaccountJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;
import grafioschtrader.rest.SecurityaccountResource;
import grafioschtrader.rest.TenantResource;
import grafioschtrader.types.TransactionType;

@DisplayName("FX observations use net conversion amounts, sample statistics and tenant scope")
class FxObservationServiceTest {
  private static final LocalDate DATE = FxMarkupEngineTest.DATE;
  private final SecurityaccountJpaRepository accounts = mock(SecurityaccountJpaRepository.class);
  private final TransactionJpaRepository transactions = mock(TransactionJpaRepository.class);
  private final CurrencypairJpaRepository pairs = mock(CurrencypairJpaRepository.class);
  private final HistoryquoteJpaRepository quotes = mock(HistoryquoteJpaRepository.class);
  private final FxObservationService service = new FxObservationService(accounts, transactions, pairs, quotes,
      new FxMarkupEngine());

  private void pair(String from, String to, double close) {
    var pair = new Currencypair(from, to);
    pair.setIdSecuritycurrency(9);
    when(pairs.findById(9)).thenReturn(Optional.of(pair));
    close(9, DATE, close);
  }

  private void close(int id, LocalDate date, double value) {
    var quote = new Historyquote();
    quote.setClose(value);
    when(quotes.findByIdSecuritycurrencyAndDate(id, date)).thenReturn(Optional.of(quote));
  }

  private Transaction tx(TransactionType type, double rate, double amount) {
    var tx = new Transaction();
    tx.setIdCurrencypair(9);
    tx.setIdTenant(7);
    tx.setIdSecurityaccount(42);
    tx.setTransactionType(type);
    tx.setTransactionTime(DATE.atStartOfDay());
    tx.setCurrencyExRate(rate);
    tx.setUnits(1.0);
    tx.setQuotation(amount);
    var security = mock(Security.class);
    when(security.getCurrency()).thenReturn("USD");
    tx.setSecuritycurrency(security);
    var cash = new Cashaccount();
    cash.setCurrency("CHF");
    tx.setCashaccount(cash);
    return tx;
  }

  private List<FxObservationGroup> report(String own, String plan, Transaction... rows) {
    when(accounts.findByIdSecuritycashAccountAndIdTenant(42, 7)).thenReturn(FeeModelResolverTest.account(own, plan));
    when(transactions.findFxAccountObservations(42, 7, null, null)).thenReturn(List.of(rows));
    return service.account(42, 7, null, null).groups();
  }

  @Test
  void onePercentBuySellAndIncomeAreAdverseInEitherStoredDirection() {
    for (boolean inverse : List.of(false, true)) {
      pair(inverse ? "CHF" : "USD", inverse ? "USD" : "CHF", 2);
      double buy = inverse ? 1.98 : 2.02;
      double sell = inverse ? 2.02 : 1.98;
      var groups = report(null, FxMarkupEngineTest.COMMISSION + FxMarkupEngineTest.flat("1"),
          tx(TransactionType.ACCUMULATE, buy, 100), tx(TransactionType.REDUCE, sell, 200),
          tx(TransactionType.DIVIDEND, sell, 10));
      assertThat(groups).hasSize(2);
      for (var group : groups) {
        assertThat(group.mean()).isCloseTo(1, within(1e-10));
        assertThat(group.median()).isCloseTo(1, within(1e-10));
        assertThat(group.volumeWeightedMean()).isCloseTo(1, within(1e-10));
        assertThat(group.modelledMean()).isEqualTo(1);
        assertThat(group.modelledCount()).isEqualTo(group.count());
      }
      assertThat(groups.get(1).stdDev()).isNull();
    }
  }

  @Test
  void skippedReasonsAreExclusiveAndEightPercentBoundaryIsExcluded() {
    pair("USD", "CHF", 2);
    var missingRate = tx(TransactionType.ACCUMULATE, 2, 100);
    missingRate.setCurrencyExRate(null);
    var missingClose = tx(TransactionType.ACCUMULATE, 2, 100);
    missingClose.setTransactionTime(DATE.minusDays(1).atStartOfDay());
    var group = report(null, null, missingRate, missingClose, tx(TransactionType.ACCUMULATE, 2.16, 100),
        tx(TransactionType.REDUCE, 1.84, 100), tx(TransactionType.ACCUMULATE, 2.02, 100)).getFirst();
    assertThat(group.count()).isOne();
    assertThat(group.skippedNoRate()).isOne();
    assertThat(group.skippedNoClose()).isOne();
    assertThat(group.skippedOutlier()).isEqualTo(2);
    assertThat(group.stdDev()).isNull();
    verify(quotes).findByIdSecuritycurrencyAndDate(9, DATE);
    var skippedOnly = report(null, null, missingRate).getFirst();
    assertThat(skippedOnly.mean()).isNull();
    assertThat(skippedOnly.median()).isNull();
  }

  @Test
  void statisticsUseSampleDeviationAndVolumeAtMidNotRecordedRate() {
    pair("USD", "CHF", 2);
    var group = report(null, null, tx(TransactionType.ACCUMULATE, 2.02, 100), tx(TransactionType.REDUCE, 1.94, 300))
        .getFirst();
    assertThat(group.mean()).isCloseTo(2, within(1e-10));
    assertThat(group.median()).isCloseTo(2, within(1e-10));
    assertThat(group.stdDev()).isCloseTo(Math.sqrt(2), within(1e-10));
    assertThat(group.volumeWeightedMean()).isCloseTo(2.5, within(1e-10));
  }

  @Test
  void netAmountsIncludeCostsTaxAndAccruedInterestAndTierReceiveUsesExistingClose() {
    pair("USD", "CHF", 2);
    String yaml = "fx:\n  amountCurrency: USD\n  rules:\n    - name: Net amount\n"
        + "      condition: 'tierAmount == 115'\n      expression: '1'\n";
    var buy = tx(TransactionType.ACCUMULATE, 2.02, 100);
    buy.setTransactionCost(5.0);
    buy.setTaxCost(3.0);
    buy.setAssetInvestmentValue1(7.0);
    var sale = tx(TransactionType.REDUCE, 1.98, 116);
    sale.setTransactionCost(5.0);
    sale.setTaxCost(3.0);
    sale.setAssetInvestmentValue1(7.0);
    var income = tx(TransactionType.DIVIDEND, 1.98, 120);
    income.setTaxCost(5.0);
    var groups = report(yaml, null, buy, sale, income);
    assertThat(groups).allSatisfy(g -> {
      assertThat(g.modelledCount()).isEqualTo(g.count());
      assertThat(g.modelledMean()).isEqualTo(1);
    });
    verify(pairs, never()).findByFromCurrencyAndToCurrencyOrToCurrencyAndFromCurrency(any(), any());
  }

  @Test
  void marginCloseConvertsItsRecordedResultNotTheNotional() {
    pair("USD", "CHF", 2);
    // A losing CFD close pays 10 USD, bought with CHF 20.20 at 2.02, although the notional is a sale of 100 USD.
    var close = tx(TransactionType.REDUCE, 2.02, 100);
    when(close.getSecurity().isMarginInstrument()).thenReturn(true);
    close.setCashaccountAmount(-20.2);
    var group = report("fx:\n  rules:\n    - name: Result\n"
        + "      condition: 'payCurrency == \"CHF\" && receiveCurrency == \"USD\" && amount > 19.99 && amount < 20.01'\n"
        + "      expression: '1'\n", null, close).getFirst();
    assertThat(group.mean()).isCloseTo(1, within(1e-10));
    assertThat(group.volumeWeightedMean()).isCloseTo(1, within(1e-10));
    assertThat(group.outcomes()).containsEntry(FxOutcome.MATCHED, 1);
  }

  @Test
  void periodsCoverageGapsAndInvalidModelsStaySeparate() {
    pair("USD", "CHF", 2);
    String yaml = "fx:\n  periods:\n    - validFrom: '2026-09-01'\n      validTo: '2026-09-25'\n"
        + "      status: VERIFIED\n      source: Tariff\n      rules:\n"
        + "        - name: September\n          condition: 'true'\n          expression: '1'\n"
        + "    - validFrom: '2026-09-26'\n      status: VERIFIED\n      source: Tariff\n      rules:\n"
        + "        - name: Later\n          condition: 'false'\n          expression: '2'\n";
    var after = tx(TransactionType.ACCUMULATE, 2.02, 100);
    after.setTransactionTime(DATE.plusDays(1).atStartOfDay());
    close(9, DATE.plusDays(1), 2);
    var before = tx(TransactionType.ACCUMULATE, 2.02, 100);
    before.setTransactionTime(LocalDate.of(2026, 8, 31).atStartOfDay());
    close(9, LocalDate.of(2026, 8, 31), 2);
    var groups = report(yaml, null, tx(TransactionType.ACCUMULATE, 2.02, 100), after, before);
    assertThat(groups).hasSize(3);
    assertThat(groups.get(0).periodFrom()).isEqualTo("2026-09-01");
    assertThat(groups.get(0).periodTo()).isEqualTo("2026-09-25");
    assertThat(groups.get(1).outcomes()).containsEntry(FxOutcome.NO_RULE, 1);
    assertThat(groups.get(2).outcomes()).containsEntry(FxOutcome.NO_PERIOD, 1);
    for (String invalid : List.of("fx: [broken]", FxMarkupEngineTest.flat("5"))) {
      var group = report(invalid, null, tx(TransactionType.ACCUMULATE, 2.02, 100)).getFirst();
      assertThat(group.mean()).isCloseTo(1, within(1e-10));
      assertThat(group.outcomes()).containsEntry(FxOutcome.INVALID, 1);
      assertThat(group.modelledMean()).isNull();
      assertThat(group.error()).isNotBlank();
    }
  }

  @Test
  void resourcesTakeTenantOnlyFromAuthenticationAndForeignAccountNeverLoadsRows() {
    var user = new User();
    user.setIdTenant(7);
    user.setActualIdTenant(99);
    var authentication = new UsernamePasswordAuthenticationToken("test", "unused");
    authentication.setDetails(user);
    SecurityContextHolder.getContext().setAuthentication(authentication);
    try {
      var accountResource = new SecurityaccountResource();
      ReflectionTestUtils.setField(accountResource, "fxObservationService", service);
      assertThat(accountResource.fxObservations(42, DATE, DATE).getBody().groups()).isEmpty();
      verify(accounts).findByIdSecuritycashAccountAndIdTenant(42, 7);
      verifyNoInteractions(transactions, pairs, quotes);
      var tenantResource = new TenantResource();
      ReflectionTestUtils.setField(tenantResource, "fxObservationService", service);
      when(transactions.findFxTransferObservations(7, DATE, DATE)).thenReturn(List.of());
      assertThat(tenantResource.fxObservations(DATE, DATE).getBody().groups()).isEmpty();
      verify(transactions).findFxTransferObservations(7, DATE, DATE);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
