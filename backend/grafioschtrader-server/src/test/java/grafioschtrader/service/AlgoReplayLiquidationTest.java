package grafioschtrader.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import grafioschtrader.entities.*;
import grafioschtrader.repository.*;
import grafioschtrader.types.*;

@DisplayName("Opening liquidation closes excluded holdings at historical executable prices")
class AlgoReplayLiquidationTest {
  private static final LocalDate OPEN = LocalDate.of(2020, 6, 19);
  private static final LocalDate MONDAY = OPEN.plusDays(3);
  private final Security security = new Security();
  private final Cashaccount cash = new Cashaccount();
  private final AlgoReplayState state = mock(AlgoReplayState.class);
  private final AlgoReplayMarketData market = mock(AlgoReplayMarketData.class);
  private final AlgoReplayCalendar calendar = mock(AlgoReplayCalendar.class);
  private final SimulationSourceRepository source = mock(SimulationSourceRepository.class);
  private final TransactionJpaRepository transactions = mock(TransactionJpaRepository.class);
  private final CurrencypairJpaRepository pairs = mock(CurrencypairJpaRepository.class);
  private final AlgoReplayCosts costs = mock(AlgoReplayCosts.class);
  private final AlgoReplayBooking booking = new AlgoReplayBooking(calendar, transactions, pairs,
      mock(HoldCashaccountBalanceJpaRepository.class), mock(GlobalparametersService.class),
      mock(org.springframework.context.MessageSource.class));
  private final AlgoReplayLiquidation liquidation = new AlgoReplayLiquidation(source, calendar, booking);

  @BeforeEach
  void setup() throws Exception {
    security.setIdSecuritycurrency(10);
    security.setName("Excluded security");
    security.setCurrency("CHF");
    security.setLeverageFactor(1);
    var asset = new Assetclass();
    asset.setSpecialInvestmentInstrument(SpecialInvestmentInstruments.CFD);
    security.setAssetClass(asset);
    cash.setIdSecuritycashAccount(30);
    cash.setCurrency("CHF");
    var run = new AlgoSimulationResult();
    run.setIdSimulationResult(7);
    run.setOpeningDate(OPEN);
    run.setEndDate(MONDAY.plusDays(3));
    var input = new AlgoReplayInputs.Instrument("CHF", "CFD", null, null, null, null, false, null, null, "STORED", null,
        List.of(), List.of(), null, null);
    ReflectionTestUtils.setField(state, "run", run);
    ReflectionTestUtils.setField(state, "market", market);
    ReflectionTestUtils.setField(state, "securities", Map.of(10, security));
    ReflectionTestUtils.setField(state, "fx",
        new AlgoReplayFx(AlgoReplayFxTest.snapshot(Map.of()), "CHF", (_, _, _) -> 1.0, _ -> {
        }));
    ReflectionTestUtils.setField(state, "inputs", new AlgoReplayInputs.Snapshot(3, false, false, 0, Map.of(), Map.of(),
        Map.of(10, input), List.of(), Map.of(10, 1f), null));
    ReflectionTestUtils.setField(state, "costs", costs);
    ReflectionTestUtils.setField(state, "roundTrips", new AlgoReplayRoundTrips());
    ReflectionTestUtils.setField(state, "taxes", mock(AlgoReplayTaxes.class));
    ReflectionTestUtils.setField(state, "locale", Locale.ENGLISH);
    when(state.idTenant()).thenReturn(66);
    when(market.tradingExcluded(security)).thenReturn(true);
    when(market.exactClose(10, MONDAY)).thenReturn(110d);
    when(costs.estimate(anyInt(), any(), anyDouble(), anyDouble(), any(), any(), anyDouble(), any()))
        .thenReturn(new AlgoReplayCosts.Estimate(2, 1, 0, null));
    when(transactions.saveOnlyAttributes(any(), isNull(), anySet())).thenAnswer(call -> call.getArgument(0));
  }

  @Test
  void longAndShortLotsSettleProfitLossNotNotional() throws Exception {
    for (TransactionType openingType : List.of(TransactionType.ACCUMULATE, TransactionType.REDUCE)) {
      var opening = trade(1, openingType, 5, OPEN, null);
      opening.setAssetInvestmentValue2(2d);
      var close = new AlgoReplayLiquidation.Close(security, 20, cash, 5, opening);
      Transaction fill = booking.closeExcluded(state, close, MONDAY);
      assertThat(fill.getConnectedIdTransaction()).isEqualTo(1);
      assertThat(fill.getAssetInvestmentValue2()).isEqualTo(2d);
      assertThat(fill.getTransactionType())
          .isEqualTo(openingType == TransactionType.ACCUMULATE ? TransactionType.REDUCE : TransactionType.ACCUMULATE);
      assertThat(fill.getCashaccountAmount()).isEqualTo(openingType == TransactionType.ACCUMULATE ? 97d : -103d);
      assertThat(fill.getAlgoFillId()).isEqualTo("7:X:10:20:1:2020-06-22");
      assertThat(fill.isSimulationOpening()).isFalse();
    }
  }

  @Test
  void inverseFundSellsItsEntireFractionalHolding() throws Exception {
    security.getAssetClass().setSpecialInvestmentInstrument(SpecialInvestmentInstruments.ETF);
    security.setLeverageFactor(-1);
    var fill = booking.closeExcluded(state, new AlgoReplayLiquidation.Close(security, 20, cash, 2.75, null), MONDAY);
    assertThat(fill.getUnits()).isEqualTo(2.75);
    assertThat(fill.getConnectedIdTransaction()).isNull();
    assertThat(fill.getCashaccountAmount()).isEqualTo(299.5);
    verify(transactions).saveOnlyAttributes(same(fill), isNull(), anySet());
  }

  @Test
  void opposingLotsPartialClosuresAndSplitsRemainSeparate() {
    var longLot = trade(1, TransactionType.ACCUMULATE, 10, OPEN.minusDays(2), null);
    var shortLot = trade(2, TransactionType.REDUCE, 10, OPEN.minusDays(2), null);
    var partial = trade(3, TransactionType.REDUCE, 3, OPEN.minusDays(1), 1);
    var splits = List.of(new Securitysplit(10, OPEN, 1, 2, CreateType.ADD_MODIFIED_USER));
    var positions = AlgoReplayLiquidation.positions(List.of(longLot, shortLot, partial), security, MONDAY, splits);
    assertThat(positions).extracting(AlgoReplayLiquidation.Close::units).containsExactly(14d, 20d);
    assertThat(positions).extracting(c -> c.opening().getId()).containsExactly(1, 2);
  }

  @Test
  void marginValidationIncludesSplitOnTheClosingDay() {
    var opening = trade(1, TransactionType.ACCUMULATE, 5, OPEN, null);
    var close = trade(2, TransactionType.REDUCE, 10, MONDAY, 1);
    close.setQuotation(55d);
    var splits = List.of(new Securitysplit(10, MONDAY, 1, 2, CreateType.ADD_MODIFIED_USER));
    var repository = mock(SecuritysplitJpaRepository.class);
    when(repository.getSecuritysplitMapByIdSecuritycurrency(10)).thenReturn(Map.of(10, splits));
    assertThat(AlgoReplayLiquidation.positions(List.of(opening), security, MONDAY, splits)).singleElement()
        .satisfies(p -> assertThat(p.units()).isEqualTo(10d));
    grafioschtrader.instrument.SecurityMarginUnitsCheck.checkUnitsIntegrity(repository,
        grafiosch.types.OperationType.ADD, List.of(opening), close, security);
    assertThat(close.validateSecurityGeneralCashaccountAmount(opening.getQuotation())).isEqualTo(50d);
  }

  @Test
  void missingHistoricalSettlementRateCannotBeReplacedWithAFutureRate() {
    cash.setCurrency("USD");
    var pair = new Currencypair("CHF", "USD");
    pair.setIdSecuritycurrency(50);
    when(pairs.findByFromCurrencyAndToCurrency("CHF", "USD")).thenReturn(pair);
    when(market.close(50, MONDAY.plusDays(1))).thenReturn(0.9);
    assertThat(booking.hasLiquidationFx(state, security, cash, MONDAY)).isFalse();
    assertThat(booking.hasLiquidationFx(state, security, cash, MONDAY.plusDays(1))).isTrue();
  }

  @Test
  void rejectedLiquidationFailsTheRunWithItsAccountAndOpeningLot() throws Exception {
    var opening = trade(1, TransactionType.REDUCE, 5, OPEN, null);
    when(transactions.saveOnlyAttributes(any(), isNull(), anySet()))
        .thenThrow(new IllegalArgumentException("REPLAY_NOT_FUNDED"));
    assertThatThrownBy(() -> liquidation.execute(state, MONDAY,
        List.of(new AlgoReplayLiquidation.Close(security, 20, cash, 5, opening))))
            .hasMessageContaining("REPLAY_LIQUIDATION_FAILED").hasMessageContaining("account=20 opening=1")
            .hasMessageContaining("REPLAY_NOT_FUNDED");
  }

  @Test
  void scheduleUsesFirstSessionAfterOpeningAndSkipsMissingExactQuote() {
    when(source.transactions(66, OPEN.plusDays(1)))
        .thenReturn(List.of(trade(1, TransactionType.ACCUMULATE, 5, OPEN, null)));
    when(calendar.nextEligibleClose(eq(security), eq(OPEN), any(), any(), any(), eq(false)))
        .thenReturn(Optional.of(MONDAY));
    when(calendar.nextEligibleClose(eq(security), eq(MONDAY), any(), any(), any(), eq(false)))
        .thenReturn(Optional.of(MONDAY.plusDays(1)));
    when(market.exactClose(10, MONDAY)).thenReturn(null);
    when(market.close(10, MONDAY)).thenReturn(100d);
    when(market.exactClose(10, MONDAY.plusDays(1))).thenReturn(111d);
    assertThat(liquidation.schedule(state)).containsOnlyKeys(MONDAY.plusDays(1));
    verifyNoInteractions(transactions);
  }

  @Test
  void unclosablePositionFailsWithInstrumentDiagnostic() {
    when(source.transactions(66, OPEN.plusDays(1)))
        .thenReturn(List.of(trade(1, TransactionType.REDUCE, 5, OPEN, null)));
    when(calendar.nextEligibleClose(eq(security), any(), any(), any(), any(), eq(false))).thenReturn(Optional.empty());
    assertThatThrownBy(() -> liquidation.schedule(state)).hasMessageContaining("REPLAY_LIQUIDATION_FAILED")
        .hasMessageContaining("Excluded security");
    verify(state).write(eq(AlgoEventType.UNAVAILABLE), eq(OPEN), isNull(), eq(10), isNull(), isNull(), isNull(),
        isNull(), eq("REPLAY_LIQUIDATION_FAILED"), contains("REPLAY_NO_LIQUIDATION_DATE"));
  }

  @Test
  void ordinaryOrderCannotReopenExcludedInstrument() {
    assertThatThrownBy(() -> booking.book(state, security, null, 5, TransactionType.ACCUMULATE, MONDAY))
        .hasMessage("REPLAY_INSTRUMENT_EXCLUDED");
    verifyNoInteractions(transactions);
  }

  private Transaction trade(int id, TransactionType type, double units, LocalDate date, Integer parent) {
    var trade = new Transaction();
    trade.setIdTransaction(id);
    trade.setSecuritycurrency(security);
    trade.setIdSecurityaccount(20);
    trade.setCashaccount(cash);
    trade.setTransactionType(type);
    trade.setTransactionTime(date.atTime(0, 0));
    trade.setUnits(units);
    trade.setQuotation(100d);
    trade.setConnectedIdTransaction(parent);
    trade.setAssetInvestmentValue2(1d);
    return trade;
  }
}
