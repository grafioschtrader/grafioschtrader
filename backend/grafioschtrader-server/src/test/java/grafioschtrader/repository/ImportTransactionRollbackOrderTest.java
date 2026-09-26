package grafioschtrader.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.entities.Transaction;
import grafioschtrader.types.TransactionType;

/**
 * Unit tests of the pure parts of the import rollback: the order in which the imported transactions are deleted and
 * the detection of transactions that carry a link an import never sets.
 *
 * <p>
 * The deletion order must be the exact reverse of the import, so that every intermediate state is one the tenant
 * already had. A cash-account transfer is removed together with its counter side by a single delete, so passing the
 * second ID as well would try to delete a transaction that no longer exists.
 * </p>
 */
@DisplayName("Import rollback deletion order and guards")
class ImportTransactionRollbackOrderTest {

  @Test
  @DisplayName("Transactions are deleted newest first by transaction ID")
  void deletesInReverseImportOrder() {
    List<Transaction> transactions = List.of(tx(11, TransactionType.ACCUMULATE, null),
        tx(13, TransactionType.DIVIDEND, null), tx(12, TransactionType.REDUCE, null));
    assertThat(ImportTransactionHeadJpaRepositoryImpl.getRollbackDeletionOrder(transactions)).containsExactly(13, 12,
        11);
  }

  @Test
  @DisplayName("A cash-account transfer is deleted once, through its newer side")
  void transferIsDeletedOnce() {
    List<Transaction> transactions = List.of(tx(20, TransactionType.WITHDRAWAL, 21),
        tx(21, TransactionType.DEPOSIT, 20), tx(22, TransactionType.FEE, null), tx(19, TransactionType.DEPOSIT, null));
    assertThat(ImportTransactionHeadJpaRepositoryImpl.getRollbackDeletionOrder(transactions)).containsExactly(22, 21,
        19);
  }

  /**
   * A finance cost carries a connected transaction ID too, but it is not a transfer: its opening position is a separate
   * transaction of the import and must be deleted by its own call.
   */
  @Test
  @DisplayName("A connected finance cost does not swallow its opening position")
  void financeCostKeepsOpeningInOrder() {
    List<Transaction> transactions = List.of(tx(30, TransactionType.ACCUMULATE, null),
        tx(31, TransactionType.FINANCE_COST, 30));
    assertThat(ImportTransactionHeadJpaRepositoryImpl.getRollbackDeletionOrder(transactions)).containsExactly(31, 30);
  }

  @Test
  @DisplayName("Plain imported transactions carry no link")
  void plainTransactionsAreNotLinked() {
    List<Transaction> transactions = List.of(tx(1, TransactionType.ACCUMULATE, null),
        tx(2, TransactionType.WITHDRAWAL, 3));
    assertThat(ImportTransactionHeadJpaRepositoryImpl.getLinkedTransactionIds(transactions)).isEmpty();
  }

  @Test
  @DisplayName("Security action, security transfer, standing order and simulation opening are reported")
  void linkedTransactionsAreReported() {
    Transaction securityAction = tx(4, TransactionType.ACCUMULATE, null);
    securityAction.setIdSecurityActionApp(1);
    Transaction securityTransfer = tx(2, TransactionType.REDUCE, null);
    securityTransfer.setIdSecurityTransfer(1);
    Transaction standingOrder = tx(3, TransactionType.DEPOSIT, null);
    standingOrder.setIdStandingOrder(1);
    Transaction opening = tx(5, TransactionType.ACCUMULATE, null);
    opening.setSimulationOpening(true);
    List<Transaction> transactions = List.of(securityAction, tx(1, TransactionType.DIVIDEND, null), securityTransfer,
        standingOrder, opening);
    assertThat(ImportTransactionHeadJpaRepositoryImpl.getLinkedTransactionIds(transactions)).containsExactly(2, 3, 4,
        5);
  }

  private static Transaction tx(int idTransaction, TransactionType transactionType, Integer connectedIdTransaction) {
    Transaction transaction = new Transaction();
    transaction.setIdTransaction(idTransaction);
    transaction.setTransactionType(transactionType);
    transaction.setConnectedIdTransaction(connectedIdTransaction);
    return transaction;
  }
}
