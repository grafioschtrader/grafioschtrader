package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafiosch.types.OperationType;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Transaction;
import grafioschtrader.instrument.SecurityGeneralUnitsCheck;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.types.CreateType;
import grafioschtrader.types.TransactionType;

@DisplayName("Replay terminal positions across settlement accounts")
class AlgoReplayTerminalHoldingsTest {
  private static final LocalDate TERMINAL = LocalDate.of(2020, 2, 7);
  private final Security security = security(3430);
  private final Cashaccount purchaseCash = cash(85279, "CHF");
  private final Cashaccount saleCash = cash(85280, "USD");

  @Test
  @DisplayName("A bond sold through a different cash account must not be redeemed again")
  void fullySoldPositionHasNoTerminalHolding() {
    List<Transaction> ledger = List.of(
        trade(85268, purchaseCash, TERMINAL.minusYears(14), TransactionType.ACCUMULATE, 500),
        trade(85268, saleCash, TERMINAL.minusYears(12), TransactionType.REDUCE, 500));

    assertThat(AlgoHistoricalReplayService.terminalHoldings(ledger, security, TERMINAL, List.of())).isEmpty();
  }

  @Test
  @DisplayName("A partial sale leaves only the custody account's net units for redemption")
  void partiallySoldPositionCanBeRedeemedWithoutOverselling() {
    List<Transaction> ledger = List.of(
        trade(85268, purchaseCash, TERMINAL.minusDays(2), TransactionType.ACCUMULATE, 500),
        trade(85268, saleCash, TERMINAL.minusDays(1), TransactionType.REDUCE, 300));

    var held = AlgoHistoricalReplayService.terminalHoldings(ledger, security, TERMINAL, List.of());

    assertThat(held).singleElement().satisfies(position -> {
      assertThat(position.units()).isEqualTo(200);
      assertThat(position.idSecurityaccount()).isEqualTo(85268);
      assertThat(position.cashaccount()).isSameAs(saleCash);
    });
    assertValidClose(ledger, held.getFirst(), List.of());
  }

  @Test
  @DisplayName("Purchases through multiple cash accounts produce one terminal fill per custody account")
  void multipleSettlementAccountsProduceOneFill() {
    List<Transaction> ledger = List.of(
        trade(85268, purchaseCash, TERMINAL.minusDays(2), TransactionType.ACCUMULATE, 200),
        trade(85268, saleCash, TERMINAL.minusDays(1), TransactionType.ACCUMULATE, 300));

    var held = AlgoHistoricalReplayService.terminalHoldings(ledger, security, TERMINAL, List.of());

    assertThat(held).singleElement().satisfies(position -> assertThat(position.units()).isEqualTo(500));
    assertThat(AlgoHistoricalReplayService.terminalHoldings(ledger.reversed(), security, TERMINAL, List.of()))
        .isEqualTo(held);
    assertValidClose(ledger, held.getFirst(), List.of());
  }

  @Test
  @DisplayName("A closed custody account cannot consume another custody account's remaining position")
  void custodyAccountsRemainSeparate() {
    List<Transaction> ledger = List.of(
        trade(85268, purchaseCash, TERMINAL.minusDays(2), TransactionType.ACCUMULATE, 500),
        trade(85271, saleCash, TERMINAL.minusDays(2), TransactionType.ACCUMULATE, 100),
        trade(85268, saleCash, TERMINAL.minusDays(1), TransactionType.REDUCE, 500));

    assertThat(AlgoHistoricalReplayService.terminalHoldings(ledger, security, TERMINAL, List.of())).singleElement()
        .satisfies(position -> {
          assertThat(position.idSecurityaccount()).isEqualTo(85271);
          assertThat(position.units()).isEqualTo(100);
        });
  }

  @Test
  @DisplayName("Terminal holdings include same-day fills, excluding future trades, income and other instruments")
  void terminalDateBoundaryAndTransactionScope() {
    Transaction otherSecurity = trade(85268, purchaseCash, TERMINAL, TransactionType.ACCUMULATE, 900);
    otherSecurity.setSecuritycurrency(security(999));
    List<Transaction> ledger = List.of(
        trade(85268, purchaseCash, TERMINAL.minusDays(1), TransactionType.ACCUMULATE, 200),
        trade(85268, saleCash, TERMINAL, TransactionType.REDUCE, 50),
        trade(85268, purchaseCash, TERMINAL.plusDays(1), TransactionType.ACCUMULATE, 300),
        trade(85268, saleCash, TERMINAL, TransactionType.DIVIDEND, 200), otherSecurity);

    assertThat(AlgoHistoricalReplayService.terminalHoldings(ledger, security, TERMINAL, List.of())).singleElement()
        .satisfies(position -> assertThat(position.units()).isEqualTo(150));
  }

  @Test
  @DisplayName("Terminal quantities apply splits through the closing day without applying future splits")
  void terminalCloseUsesTheCorrectSplitBasis() {
    List<Securitysplit> splits = List.of(
        new Securitysplit(security.getId(), TERMINAL, 2, 1, CreateType.ADD_MODIFIED_USER),
        new Securitysplit(security.getId(), TERMINAL.plusDays(1), 1, 3, CreateType.ADD_MODIFIED_USER));
    List<Transaction> ledger = List.of(
        trade(85268, purchaseCash, TERMINAL.minusDays(2), TransactionType.ACCUMULATE, 500),
        trade(85268, saleCash, TERMINAL, TransactionType.REDUCE, 50));

    var held = AlgoHistoricalReplayService.terminalHoldings(ledger, security, TERMINAL, splits);

    assertThat(held).singleElement().satisfies(position -> assertThat(position.units()).isEqualTo(200));
    assertValidClose(ledger, held.getFirst(), splits);
  }

  private void assertValidClose(List<Transaction> ledger, AlgoHistoricalReplayService.PositionHolding position,
      List<Securitysplit> splits) {
    SecuritysplitJpaRepository repository = mock(SecuritysplitJpaRepository.class);
    when(repository.getSecuritysplitMapByIdSecuritycurrency(security.getId()))
        .thenReturn(Map.of(security.getId(), splits));
    Transaction close = trade(position.idSecurityaccount(), position.cashaccount(), TERMINAL, TransactionType.REDUCE,
        position.units());
    assertThatCode(
        () -> SecurityGeneralUnitsCheck.checkUnitsIntegrity(repository, OperationType.ADD, ledger, close, security))
            .doesNotThrowAnyException();
  }

  private Transaction trade(int custody, Cashaccount cash, LocalDate date, TransactionType type, double units) {
    Transaction transaction = new Transaction();
    transaction.setSecuritycurrency(security);
    transaction.setIdSecurityaccount(custody);
    transaction.setCashaccount(cash);
    transaction.setTransactionTime(date.atStartOfDay());
    transaction.setTransactionType(type);
    transaction.setUnits(units);
    return transaction;
  }

  private static Security security(int id) {
    Security security = new Security();
    security.setIdSecuritycurrency(id);
    security.setCurrency("USD");
    return security;
  }

  private static Cashaccount cash(int id, String currency) {
    Cashaccount cash = new Cashaccount();
    cash.setIdSecuritycashAccount(id);
    cash.setCurrency(currency);
    return cash;
  }
}
