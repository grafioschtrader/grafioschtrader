package grafioschtrader.dto;

import java.time.LocalDateTime;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import grafiosch.exceptions.DataViolationException;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Transaction;
import grafioschtrader.types.TransactionType;

/**
 * Guards the construction of a cash account transfer, because {@code connectTransactions()} writes whatever the two
 * sides happen to be into {@code con_id_transaction} of both rows. A pair that repeats one transaction produces a row
 * pointing at itself, which no database constraint forbids and which later aborts the rebuild of the cash account
 * deposit holdings.
 */
class CashAccountTransferPairTest {

  private static final LocalDateTime TRANSFER_TIME = LocalDateTime.of(2026, 5, 31, 12, 0);

  @Test
  void sidesAreAssignedByTypeNotByPosition() {
    Transaction withdrawal = transaction(1, TransactionType.WITHDRAWAL, "CHF", -100.0);
    Transaction deposit = transaction(2, TransactionType.DEPOSIT, "USD", 110.0);

    CashAccountTransfer transfer = new CashAccountTransfer(List.of(deposit, withdrawal));

    Assertions.assertThat(transfer.getWithdrawalTransaction()).isSameAs(withdrawal);
    Assertions.assertThat(transfer.getDepositTransaction()).isSameAs(deposit);
  }

  @Test
  void aMissingExistingSideKeepsTheOtherOnItsOwnSide() {
    Transaction deposit = transaction(2, TransactionType.DEPOSIT, "USD", 110.0);

    CashAccountTransfer transfer = new CashAccountTransfer(new Transaction[] { deposit, null });

    Assertions.assertThat(transfer.getDepositTransaction()).isSameAs(deposit);
    Assertions.assertThat(transfer.getWithdrawalTransaction()).isNull();
  }

  @Test
  void theSameTransactionOnBothSidesIsRejected() {
    Transaction withdrawal = transaction(1, TransactionType.WITHDRAWAL, "CHF", -100.0);

    Assertions.assertThatThrownBy(() -> CashAccountTransfer.validatePair(withdrawal, withdrawal))
        .isInstanceOf(DataViolationException.class);
  }

  @Test
  void twoSidesOfTheSameTypeAreRejected() {
    Transaction firstDeposit = transaction(1, TransactionType.DEPOSIT, "CHF", 100.0);
    Transaction secondDeposit = transaction(2, TransactionType.DEPOSIT, "USD", 110.0);

    Assertions.assertThatThrownBy(() -> new CashAccountTransfer(List.of(firstDeposit, secondDeposit)))
        .isInstanceOf(DataViolationException.class);
  }

  @Test
  void twoNewSidesWithoutAnIdAreAccepted() {
    Transaction withdrawal = transaction(null, TransactionType.WITHDRAWAL, "CHF", -100.0);
    Transaction deposit = transaction(null, TransactionType.DEPOSIT, "USD", 110.0);

    CashAccountTransfer transfer = new CashAccountTransfer(List.of(withdrawal, deposit));

    Assertions.assertThat(transfer.getWithdrawalTransaction()).isSameAs(withdrawal);
    Assertions.assertThat(transfer.getDepositTransaction()).isSameAs(deposit);
  }

  private Transaction transaction(Integer id, TransactionType transactionType, String currency, double amount) {
    Transaction transaction = new Transaction(new Cashaccount(id == null ? 10 : id + 10, currency), amount,
        transactionType, TRANSFER_TIME);
    transaction.setIdTransaction(id);
    return transaction;
  }
}
