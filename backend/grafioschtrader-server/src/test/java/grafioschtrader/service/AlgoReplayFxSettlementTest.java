package grafioschtrader.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import grafioschtrader.dto.*;
import grafioschtrader.entities.*;
import grafioschtrader.repository.*;
import grafioschtrader.types.*;

/** Exercises actual replay settlement and funding against an in-memory cash ledger, without a Spring database. */
@DisplayName("FX replay settlement and funding plans")
class AlgoReplayFxSettlementTest {
  private static final LocalDate DAY = AlgoReplayFxTest.DATE;
  private final AlgoReplayState state = mock(AlgoReplayState.class);
  private final TransactionJpaRepository transactions = mock(TransactionJpaRepository.class);
  private final CurrencypairJpaRepository currencies = mock(CurrencypairJpaRepository.class);
  private final HoldCashaccountBalanceJpaRepository balances = mock(HoldCashaccountBalanceJpaRepository.class);
  private final AlgoReplayMarketData market = mock(AlgoReplayMarketData.class);
  private final AlgoReplayCalendar calendar = mock(AlgoReplayCalendar.class);
  private final GlobalparametersService parameters = mock(GlobalparametersService.class);
  private final Map<Integer, Double> cash = new HashMap<>();
  private final List<CashAccountTransfer> transfers = new ArrayList<>();
  private final List<Transaction> fills = new ArrayList<>();
  private final Portfolio portfolio = new Portfolio(65, "Broker", "CHF");
  private final Security security = new Security();
  private AlgoReplayBooking booking;
  private AlgoReplayFx fx;

  @BeforeEach
  void setup() throws Exception {
    portfolio.setIdPortfolio(1);
    security.setIdSecuritycurrency(10);
    security.setCurrency("USD");
    security.setName("USD security");
    var asset = new Assetclass();
    asset.setSpecialInvestmentInstrument(SpecialInvestmentInstruments.DIRECT_INVESTMENT);
    asset.setCategoryType(AssetclassType.EQUITIES);
    security.setAssetClass(asset);
    var run = new AlgoSimulationResult();
    run.setIdSimulationResult(1);
    run.setEndDate(DAY.plusYears(1));
    ReflectionTestUtils.setField(state, "run", run);
    ReflectionTestUtils.setField(state, "market", market);
    ReflectionTestUtils.setField(state, "roundTrips", new AlgoReplayRoundTrips());
    when(state.idTenant()).thenReturn(65);
    when(parameters.getPrecisionForCurrency(any())).thenReturn(2);
    when(balances.getBalanceBeforeDate(any(), any())).thenAnswer(a -> cash.getOrDefault(a.getArgument(0), 0.0));
    when(balances.getMinBalanceFromDate(any(), any())).thenAnswer(a -> cash.getOrDefault(a.getArgument(0), 0.0));
    when(calendar.nextEligibleClose(any(), any(), any(), any(), any(), any())).thenReturn(Optional.of(DAY.plusDays(1)));
    when(market.close(eq(10), any())).thenReturn(100.0);
    when(market.exactClose(eq(10), any())).thenReturn(100.0);
    pair("USD", "CHF", 20, .8);
    pair("EUR", "USD", 21, 1.2);
    pair("EUR", "CHF", 22, .96);
    when(transactions.saveOnlyAttributes(any(), isNull(), anySet())).thenAnswer(a -> {
      Transaction fill = a.getArgument(0);
      fills.add(fill);
      cash.merge(fill.getCashaccount().getId(), fill.getCashaccountAmount(), Double::sum);
      return fill;
    });
    when(transactions.updateCreateCashaccountTransfer(any(), any())).thenAnswer(a -> {
      CashAccountTransfer transfer = a.getArgument(0);
      transfers.add(transfer);
      for (var row : List.of(transfer.getWithdrawalTransaction(), transfer.getDepositTransaction()))
        cash.merge(row.getCashaccount().getId(), row.getCashaccountAmount(), Double::sum);
      return transfer;
    });
    booking = new AlgoReplayBooking(calendar, transactions, currencies, balances, parameters,
        mock(MessageSource.class));
  }

  private void pair(String from, String to, int id, double close) {
    var pair = new Currencypair(from, to);
    pair.setIdSecuritycurrency(id);
    when(currencies.findByFromCurrencyAndToCurrency(from, to)).thenReturn(pair);
    when(currencies.getHistoricalClosePriceForDate(eq(pair), any())).thenReturn(close);
    when(market.close(eq(id), any())).thenReturn(close);
  }

  private Cashaccount account(int id, String currency, double amount) {
    var result = new Cashaccount("Cash " + id, null, currency, portfolio);
    result.setIdSecuritycashAccount(id);
    cash.put(id, amount);
    return result;
  }

  private Securityaccount securities(int id) {
    var account = new Securityaccount();
    account.setIdSecuritycashAccount(id);
    account.setPortfolio(portfolio);
    return account;
  }

  private void configure(Map<Integer, FxFeeConfig> models, List<Securityaccount> accounts,
      List<Cashaccount> cashaccounts) {
    var instrument = new AlgoReplayInputs.Instrument("USD", "DIRECT_INVESTMENT", "EQUITIES", "XNYS", null, null, false,
        null, null, "STORED", null, List.of(), List.of(), null, null);
    var inputs = new AlgoReplayInputs.Snapshot(5, false, false, 0, Map.of(), Map.of(), Map.of(10, instrument),
        List.of()).withFees(Map.of(), Map.of(), models);
    fx = new AlgoReplayFx(inputs, "CHF", booking.fxRates(market), _ -> {
    });
    var taxes = new AlgoReplayTaxes(inputs, null, DAY.minusDays(1), Map.of());
    ReflectionTestUtils.setField(state, "inputs", inputs);
    ReflectionTestUtils.setField(state, "fx", fx);
    ReflectionTestUtils.setField(state, "taxes", taxes);
    ReflectionTestUtils.setField(state, "costs",
        new AlgoReplayCosts(new AlgoReplayFees(accounts, null, Map.of()), taxes, inputs));
    ReflectionTestUtils.setField(state, "accounts",
        new AlgoReplayAccounts(accounts, cashaccounts, List.of(), "CHF", Map.of()));
  }

  @Test
  void buySellRedemptionAndTerminalCloseCarryTheMarkup() throws Exception {
    var chf = account(40, "CHF", 10000);
    configure(Map.of(30, AlgoReplayFxTest.flat("1")), List.of(securities(30)), List.of(chf));
    var buy = booking.book(state, security, null, 1, TransactionType.ACCUMULATE, DAY);
    assertThat(buy.getCurrencyExRate()).isCloseTo(.808, within(1e-12));
    assertThat(buy.getCashaccountAmount()).isCloseTo(-80.8, within(1e-12));
    var sell = booking.book(state, security, null, 1, TransactionType.REDUCE, DAY.plusDays(1));
    var redeem = booking.redeem(state, security, null, 30, chf, 1, DAY.plusDays(2));
    var terminal = booking.terminalClose(state, security, 30, chf, 1, DAY.plusDays(3), null);
    for (Transaction row : List.of(sell, redeem, terminal))
      assertThat(row.getCurrencyExRate()).isCloseTo(.792, within(1e-12));
    assertThat(fx.paid()).isCloseTo(3.2, within(1e-10));
  }

  @Test
  void openingLiquidationUsesTheSameRateAndCommitAccounting() throws Exception {
    var chf = account(40, "CHF", 0);
    configure(Map.of(30, AlgoReplayFxTest.flat("1")), List.of(securities(30)), List.of(chf));
    when(market.tradingExcluded(security)).thenReturn(true);
    var fill = booking.closeExcluded(state, new AlgoReplayLiquidation.Close(security, 30, chf, 1, null), DAY);
    assertThat(fill.getCurrencyExRate()).isCloseTo(.792, within(1e-12));
    assertThat(fx.paid()).isCloseTo(.8, within(1e-12));
  }

  @Test
  void accountsSharingCashArePricedUnderTheirOwnTariff() {
    var chf = account(40, "CHF", 80.5);
    configure(Map.of(30, AlgoReplayFxTest.flat("1"), 31, AlgoReplayFxTest.flat("0")),
        List.of(securities(30), securities(31)), List.of(chf));
    var funds = booking.funds(state, security);
    assertThat(funds.availableFor(30, chf, DAY, "USD", 100)).isLessThan(100);
    assertThat(funds.availableFor(31, chf, DAY, "USD", 100)).isGreaterThan(100);
    assertThat(state.accounts.resolve(security, DAY, 100, funds).idSecurityaccount()).isEqualTo(31);
    assertThat(fx.paid()).isZero();
  }

  @Test
  void reducedOrderRequotesItsFinalTierAndMic() throws Exception {
    var chf = account(40, "CHF", 810);
    var model = new FxFeeConfig(null, null,
        List.of(new FeeRule("large", "amount >= 850", "0"), new FeeRule("small NYSE", "mic = \"XNYS\"", "4")), null);
    configure(Map.of(30, model), List.of(securities(30)), List.of(chf));
    var fill = booking.book(state, security, null, 11, TransactionType.ACCUMULATE, DAY);
    assertThat(fill.getUnits()).isEqualTo(9);
    assertThat(fill.getCurrencyExRate()).isCloseTo(.832, within(1e-12));
    assertThat(cash.get(40)).isGreaterThanOrEqualTo(0);
    assertThat(fx.paid()).isCloseTo(28.8, within(1e-10));
  }

  @Test
  void strategyEstimatesAndRejectedFillsRecordNothing() throws Exception {
    var chf = account(40, "CHF", 1000);
    configure(Map.of(30, AlgoReplayFxTest.flat("1")), List.of(securities(30)), List.of(chf));
    booking.book(state, security, 70, 1, TransactionType.ACCUMULATE, DAY);
    assertThat(fills).isEmpty();
    assertThat(fx.paid()).isZero();
    doThrow(new IllegalArgumentException("rejected")).when(transactions).saveOnlyAttributes(any(), isNull(), anySet());
    assertThatThrownBy(() -> booking.book(state, security, null, 1, TransactionType.ACCUMULATE, DAY))
        .hasMessage("rejected");
    assertThat(fx.paid()).isZero();
  }

  @Test
  void positiveMarkupFundsUsdDirectlyAndNeverExecutesTheLosingPlan() throws Exception {
    var usd = account(40, "USD", 60);
    var moreUsd = account(41, "USD", 50);
    var chf = account(42, "CHF", 0);
    configure(Map.of(30, AlgoReplayFxTest.flat("1")), List.of(securities(30)), List.of(usd, moreUsd, chf));
    var fill = booking.book(state, security, null, 1, TransactionType.ACCUMULATE, DAY);
    assertThat(fill.getCashaccount()).isSameAs(usd);
    assertThat(transfers).hasSize(1);
    assertThat(transfers.getFirst().getDepositTransaction().getCashaccount()).isSameAs(usd);
    assertThat(transfers.getFirst().getDepositTransaction().getCurrencyExRate()).isNull();
    assertThat(fx.paid()).isZero();
    assertThat(cash.get(42)).isZero();
  }

  @Test
  void zeroAndAbsentFxKeepTheOriginalChfFundingLedger() throws Exception {
    var usd = account(40, "USD", 60);
    var moreUsd = account(41, "USD", 50);
    var chf = account(42, "CHF", 0);
    List<Double> expected = null;
    for (var models : List.of(Map.<Integer, FxFeeConfig>of(), Map.of(30, AlgoReplayFxTest.flat("0")))) {
      cash.put(40, 60.0);
      cash.put(41, 50.0);
      cash.put(42, 0.0);
      transfers.clear();
      state.fundingTransfers = 0;
      configure(models, List.of(securities(30)), List.of(usd, moreUsd, chf));
      var fill = booking.book(state, security, null, 1, TransactionType.ACCUMULATE, DAY);
      assertThat(fill.getCashaccount()).isSameAs(chf);
      assertThat(transfers).hasSize(2);
      var ledger = transfers.stream()
          .flatMap(t -> java.util.stream.Stream.of(t.getWithdrawalTransaction(), t.getDepositTransaction()))
          .map(Transaction::getCashaccountAmount).toList();
      if (expected == null)
        expected = ledger;
      else
        assertThat(ledger).isEqualTo(expected);
      assertThat(fx.paid()).isZero();
    }
  }

  @Test
  void pinnedAccountStaysPinnedAndCommittedTransferSurvivesRefusedPurchase() throws Exception {
    var usd = account(40, "USD", 200);
    var chf = account(42, "CHF", 0);
    configure(Map.of(30, AlgoReplayFxTest.flat("1")), List.of(securities(30)), List.of(usd, chf));
    state.accounts.remember(security, new AlgoReplayAccounts.Booking(30, chf));
    doThrow(new IllegalArgumentException("rejected")).when(transactions).saveOnlyAttributes(any(), isNull(), anySet());
    assertThatThrownBy(() -> booking.book(state, security, null, 1, TransactionType.ACCUMULATE, DAY))
        .hasMessage("rejected");
    assertThat(transfers).hasSize(1);
    var deposit = transfers.getFirst().getDepositTransaction();
    assertThat(deposit.getCashaccount()).isSameAs(chf);
    assertThat(deposit.getCurrencyExRate()).isCloseTo(.792, within(1e-12));
    double expected = (deposit.getCashaccountAmount() / .792 - deposit.getCashaccountAmount() / .8) * .8;
    assertThat(fx.paid()).isCloseTo(expected, within(1e-10));
  }

  @Test
  void incomeChargesOnlyNetSettlementAndSameCurrencyIncomeStaysFree() throws Exception {
    var usd = account(40, "USD", 0);
    var chf = account(42, "CHF", 0);
    configure(Map.of(30, AlgoReplayFxTest.flat("1")), List.of(securities(30)), List.of(usd, chf));
    var service = income();
    var context = new AlgoReplayIncomeBookingService.Context(1, 65, "CHF", market, fx);
    var dividend = new AlgoReplayDividendService.Distribution(1, security, DAY.minusDays(2), DAY, 100.0, "EUR", false);
    var local = service.pay(context, new AlgoReplayDividendService.Claim(dividend, 30, usd, 1), 1, 20);
    assertThat(fx.paid()).isZero();
    assertThat(local.net()).isCloseTo(80, within(1e-10));
    var foreign = service.pay(context, new AlgoReplayDividendService.Claim(dividend, 30, chf, 1), 1, 20);
    assertThat(foreign.gross()).isCloseTo(96, within(1e-10));
    assertThat(foreign.withholding()).isCloseTo(16, within(1e-10));
    assertThat(foreign.net()).isCloseTo(79.2, within(1e-10));
    assertThat(foreign.gross() - foreign.withholding()).isCloseTo(foreign.net() + fx.paid(), within(1e-10));
  }

  private AlgoReplayIncomeBookingService income() {
    var template = mock(TransactionTemplate.class);
    when(template.execute(any())).thenAnswer(a -> ((TransactionCallback<?>) a.getArgument(0)).doInTransaction(null));
    var days = mock(TradingDaysPlusJpaRepository.class);
    when(days.findByTradingDateBetweenOrderByTradingDate(any(), any())).thenReturn(List.of());
    return new AlgoReplayIncomeBookingService(mock(AlgoTradingRepository.class), transactions, currencies, template,
        days);
  }

  @Test
  void custodyTransferCountsOneConversionAndReproducesTheWorkedCharge() {
    var usd = account(40, "USD", 100);
    var chf = account(42, "CHF", 0);
    configure(Map.of(30, AlgoReplayFxTest.flat("1")), List.of(securities(30)), List.of(usd, chf));
    booking.fundCustody(state, 30, chf, DAY.plusDays(1), 79.2);
    assertThat(transfers).hasSize(1);
    assertThat(transfers.getFirst().getWithdrawalTransaction().getCashaccountAmount()).isEqualTo(-100);
    assertThat(transfers.getFirst().getDepositTransaction().getCashaccountAmount()).isEqualTo(79.2);
    assertThat(fx.paid()).isCloseTo(.8, within(1e-10));
  }

  @Test
  void transferReducedAcrossATierBoundaryUsesItsFinalTariff() {
    var usd = account(40, "USD", 100);
    var chf = account(42, "CHF", 0);
    var model = new FxFeeConfig(null, null,
        List.of(new FeeRule("large", "amount >= 99", "2"), new FeeRule("small", "true", "4")), null);
    configure(Map.of(30, model), List.of(securities(30)), List.of(usd, chf));
    booking.fundCustody(state, 30, chf, DAY.plusDays(1), 80);
    var transfer = transfers.getFirst();
    assertThat(transfer.getDepositTransaction().getCurrencyExRate()).isCloseTo(.768, within(1e-12));
    assertThat(cash.get(40)).isGreaterThanOrEqualTo(0);
    assertThat(transfer.getDepositTransaction().getCashaccountAmount()).isCloseTo(76.8, within(.01));
  }

  @Test
  void allTradeCostsSelectTheTariffBeforeTheRateIsApplied() throws Exception {
    var chf = account(40, "CHF", 1000);
    var model = new FxFeeConfig(null, null,
        List.of(new FeeRule("net", "amount >= 89", "2"), new FeeRule("gross only", "true", "1")), null);
    configure(Map.of(30, model), List.of(securities(30)), List.of(chf));
    var fees = mock(AlgoReplayFees.class);
    when(fees.cost(any(), any(), anyDouble(), anyDouble(), any(), any(), anyDouble(), any())).thenReturn(12.0);
    ReflectionTestUtils.setField(state, "costs", new AlgoReplayCosts(fees, state.taxes, state.inputs));
    var buy = booking.book(state, security, null, 1, TransactionType.ACCUMULATE, DAY);
    assertThat(buy.getCurrencyExRate()).isCloseTo(.816, within(1e-12));
    assertThat(buy.getCashaccountAmount()).isCloseTo(-112 * .816, within(1e-10));
    assertThat(fx.paid()).isCloseTo(112 * .016, within(1e-10));
  }

  @Test
  void invalidTariffEscapesFundingAndNeverBooks() {
    var chf = account(40, "CHF", 1000);
    configure(Map.of(30, AlgoReplayFxTest.flat("5")), List.of(securities(30)), List.of(chf));
    assertThatThrownBy(() -> booking.book(state, security, null, 1, TransactionType.ACCUMULATE, DAY))
        .isInstanceOf(AlgoReplayFx.Failure.class);
    assertThat(transfers).isEmpty();
    assertThat(fills).isEmpty();
  }

  @Test
  void shiftedIncomeAtFourPointNinePercentPassesTheActualWritePathRateGuard() {
    var chf = account(42, "CHF", 0);
    configure(Map.of(30, AlgoReplayFxTest.flat("4.9")), List.of(securities(30)), List.of(chf));
    var template = mock(TransactionTemplate.class);
    when(template.execute(any())).thenAnswer(a -> ((TransactionCallback<?>) a.getArgument(0)).doInTransaction(null));
    var days = mock(TradingDaysPlusJpaRepository.class);
    var next = mock(TradingDaysPlus.class);
    when(next.getTradingDate()).thenReturn(DAY.plusDays(2));
    when(days.findByTradingDateBetweenOrderByTradingDate(any(), any())).thenReturn(List.of(next));
    var service = new AlgoReplayIncomeBookingService(mock(AlgoTradingRepository.class), transactions, currencies,
        template, days);
    var dividend = new AlgoReplayDividendService.Distribution(1, security, DAY.minusDays(2), DAY, 100.0, "USD", false);
    service.pay(new AlgoReplayIncomeBookingService.Context(1, 65, "CHF", market, fx),
        new AlgoReplayDividendService.Claim(dividend, 30, chf, 1), 1, 0);
    Transaction fill = fills.getFirst();
    assertThat(fill.getTransactionDate()).isEqualTo(DAY.plusDays(2));
    assertThat(fill.getCurrencyExRate()).isCloseTo(.7608, within(1e-12));
    var writer = new TransactionJpaRepositoryImpl();
    ReflectionTestUtils.setField(writer, "currencypairJpaRepository", currencies);
    when(currencies.getClosePriceForDate(any(), eq(DAY.plusDays(2)))).thenReturn(.8);
    assertThatCode(() -> ReflectionTestUtils.invokeMethod(writer, "checkCurrencypair", fill))
        .doesNotThrowAnyException();
    verify(currencies).getClosePriceForDate(any(), eq(DAY.plusDays(2)));
  }

  @Test
  void missingExactTierCloseUsesMidEvenWhenValuationCanCarryAnOlderClose() throws Exception {
    var chf = account(42, "CHF", 1000);
    var model = new FxFeeConfig("EUR", null, List.of(new FeeRule("tier", "tierAmount > 0", "1")), null);
    configure(Map.of(30, model), List.of(securities(30)), List.of(chf));
    var eur = currencies.findByFromCurrencyAndToCurrency("EUR", "CHF");
    when(currencies.getHistoricalClosePriceForDate(eq(eur), any())).thenReturn(null);
    var fill = booking.book(state, security, null, 1, TransactionType.ACCUMULATE, DAY);
    assertThat(fill.getCurrencyExRate()).isEqualTo(.8);
    assertThat(fx.uncovered()).isEqualTo(1);
    assertThat(fx.paid()).isZero();
  }
}
