package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.MessageSource;
import org.springframework.test.util.ReflectionTestUtils;

import grafioschtrader.dto.CashAccountTransfer;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.HoldCashaccountBalanceJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;

class AlgoReplayBookingTest {

  @Test
  @DisplayName("Ordinary replay orders are floored to whole units")
  void ordinaryOrdersUseWholeUnits() {
    double lot = AlgoReplayBooking.tradableLot(instrument(false, null));

    assertThat(lot).isEqualTo(1.0);
    assertThat(AlgoReplayBooking.tradableUnits(55.5555, lot)).isEqualTo(55.0);
    assertThat(AlgoReplayBooking.tradableUnits(-3.75, lot)).isEqualTo(3.0);
    assertThat(AlgoReplayBooking.tradableUnits(0.75, lot)).isZero();
  }

  @Test
  @DisplayName("Direct bond replay orders use denomination on GT's nominal-per-100 unit basis")
  void directBondOrdersUseDenomination() {
    double lot = AlgoReplayBooking.tradableLot(instrument(true, 5_000));

    assertThat(lot).isEqualTo(50.0);
    assertThat(AlgoReplayBooking.tradableUnits(149.99, lot)).isEqualTo(100.0);
    assertThat(AlgoReplayBooking.tradableUnits(49.99, lot)).isZero();
  }

  @Test
  @DisplayName("A missing or invalid bond denomination falls back to one stored unit")
  void missingBondDenominationUsesWholeUnits() {
    assertThat(AlgoReplayBooking.tradableLot(instrument(true, null))).isEqualTo(1.0);
    assertThat(AlgoReplayBooking.tradableLot(instrument(true, 0))).isEqualTo(1.0);
  }

  @Test
  @DisplayName("Version one replay inputs without denomination remain readable")
  void oldSnapshotWithoutDenominationRemainsReadable() {
    String json = """
        {"version":1,"applyTaxModels":false,"generateBondCoupons":false,"dividendDelay":0,
         "countryModels":{},"accounts":{},"instruments":{"7":{"currency":"CHF","directBond":true,
         "observations":[],"splits":[]}}}
        """;

    AlgoReplayInputs.Instrument restored = AlgoReplayInputs.read(json).instruments().get(7);

    assertThat(restored.denomination()).isNull();
    assertThat(restored.activeFromDate()).isNull();
    assertThat(restored.activeToDate()).isNull();
    assertThat(AlgoReplayBooking.tradableLot(restored)).isEqualTo(1.0);
  }

  @Test
  @DisplayName("A custody charge is funded the day before, because money arriving on its own day pays nothing")
  void custodyFundingIsBookedTheDayBeforeTheCharge() throws Exception {
    var transactions = fundCustody(180.0, 1000.0, 200.0);

    var transfer = ArgumentCaptor.forClass(CashAccountTransfer.class);
    verify(transactions).updateCreateCashaccountTransfer(transfer.capture(), any());
    assertThat(transfer.getValue().getDepositTransaction().getTransactionDate()).isEqualTo(CHARGE.minusDays(1));
    assertThat(transfer.getValue().getWithdrawalTransaction().getTransactionDate()).isEqualTo(CHARGE.minusDays(1));
    assertThat(transfer.getValue().getDepositTransaction().getCashaccountAmount()).isEqualTo(20.0);
  }

  @Test
  @DisplayName("A shortfall of a few cents is funded with the minimum transfer, so no cent transfer is written")
  void centShortfallIsRaisedToTheMinimumTransfer() throws Exception {
    var transactions = fundCustody(199.98, 1000.0, 200.0);

    var transfer = ArgumentCaptor.forClass(CashAccountTransfer.class);
    verify(transactions).updateCreateCashaccountTransfer(transfer.capture(), any());
    assertThat(transfer.getValue().getDepositTransaction().getCashaccountAmount()).isEqualTo(10.0);
    assertThat(transfer.getValue().getWithdrawalTransaction().getCashaccountAmount()).isEqualTo(-10.0);
  }

  @Test
  @DisplayName("A source holding less than the minimum transfer keeps its residue")
  void residueBelowTheMinimumIsNotSwept() throws Exception {
    var transactions = fundCustody(180.0, 5.0, 200.0);

    verify(transactions, never()).updateCreateCashaccountTransfer(any(), any());
  }

  private static final LocalDate CHARGE = LocalDate.of(2020, 6, 30);

  /** Funds a custody charge on a CHF account from one other CHF account and returns the transaction writer. */
  private static TransactionJpaRepository fundCustody(double targetBalance, double sourceBalance, double charge)
      throws Exception {
    LocalDate dayBefore = CHARGE.minusDays(1);
    Cashaccount target = cashaccount(40, "Migros CHF");
    Cashaccount source = cashaccount(41, "Migros CHF 2");
    var holdings = mock(HoldCashaccountBalanceJpaRepository.class);
    when(holdings.getBalanceBeforeDate(40, CHARGE)).thenReturn(targetBalance);
    when(holdings.getMinBalanceFromDate(40, CHARGE)).thenReturn(targetBalance);
    when(holdings.getBalanceBeforeDate(41, dayBefore)).thenReturn(sourceBalance);
    when(holdings.getMinBalanceFromDate(41, dayBefore)).thenReturn(sourceBalance);
    var globalparameters = mock(GlobalparametersService.class);
    when(globalparameters.getPrecisionForCurrency("CHF")).thenReturn(2);
    var transactions = mock(TransactionJpaRepository.class);
    var booking = new AlgoReplayBooking(mock(AlgoReplayCalendar.class), transactions,
        mock(CurrencypairJpaRepository.class), holdings, globalparameters, mock(MessageSource.class));
    var accounts = mock(AlgoReplayAccounts.class);
    when(accounts.fundingSources(target, dayBefore)).thenReturn(List.of(source));
    var state = mock(AlgoReplayState.class);
    ReflectionTestUtils.setField(state, "accounts", accounts);
    when(state.idTenant()).thenReturn(72);

    var run = new grafioschtrader.entities.AlgoSimulationResult();
    run.setIdSimulationResult(1);
    ReflectionTestUtils.setField(state, "run", run);
    ReflectionTestUtils.setField(state, "fx", mock(AlgoReplayFx.class));
    booking.fundCustody(state, 30, target, CHARGE, charge);
    return transactions;
  }

  private static Cashaccount cashaccount(int id, String name) {
    Cashaccount cashaccount = new Cashaccount();
    cashaccount.setIdSecuritycashAccount(id);
    cashaccount.setName(name);
    cashaccount.setCurrency("CHF");
    return cashaccount;
  }

  private AlgoReplayInputs.Instrument instrument(boolean directBond, Integer denomination) {
    return new AlgoReplayInputs.Instrument("CHF", null, null, null, null, null, directBond, denomination, null, null,
        null, List.of(), List.of(), null, null);
  }
}
