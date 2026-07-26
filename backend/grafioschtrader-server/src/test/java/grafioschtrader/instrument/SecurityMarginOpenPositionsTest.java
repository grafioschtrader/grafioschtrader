package grafioschtrader.instrument;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.entities.Transaction;
import grafioschtrader.types.TransactionType;

/**
 * Pure unit tests for {@link SecurityMarginUnitsCheck#getOpenPositionsAt(List, LocalDateTime)}, which the transaction
 * import uses to link an imported finance cost to its open margin position.
 */
class SecurityMarginOpenPositionsTest {

  private static final LocalDateTime EVAL_TIME = LocalDateTime.of(2026, 6, 15, 12, 0);

  private Transaction margin(Integer idTransaction, TransactionType transactionType, Integer connectedIdTransaction,
      double units, LocalDateTime transactionTime) {
    Transaction transaction = new Transaction();
    transaction.setIdTransaction(idTransaction);
    transaction.setTransactionType(transactionType);
    transaction.setConnectedIdTransaction(connectedIdTransaction);
    transaction.setUnits(units);
    transaction.setTransactionTime(transactionTime);
    return transaction;
  }

  @Test
  @DisplayName("A single opening position without closes is open")
  void singleOpenPosition() {
    Transaction open = margin(1, TransactionType.ACCUMULATE, null, 10, EVAL_TIME.minusDays(30));
    assertThat(SecurityMarginUnitsCheck.getOpenPositionsAt(List.of(open), EVAL_TIME)).containsExactly(open);
  }

  @Test
  @DisplayName("A fully closed position is not open")
  void fullyClosedPosition() {
    Transaction open = margin(1, TransactionType.ACCUMULATE, null, 10, EVAL_TIME.minusDays(30));
    Transaction close = margin(2, TransactionType.REDUCE, 1, 10, EVAL_TIME.minusDays(5));
    assertThat(SecurityMarginUnitsCheck.getOpenPositionsAt(List.of(open, close), EVAL_TIME)).isEmpty();
  }

  @Test
  @DisplayName("A partially closed position stays open")
  void partiallyClosedPosition() {
    Transaction open = margin(1, TransactionType.ACCUMULATE, null, 10, EVAL_TIME.minusDays(30));
    Transaction close = margin(2, TransactionType.REDUCE, 1, 4, EVAL_TIME.minusDays(5));
    assertThat(SecurityMarginUnitsCheck.getOpenPositionsAt(List.of(open, close), EVAL_TIME)).containsExactly(open);
  }

  @Test
  @DisplayName("A close after the evaluation time does not count, the position was still open")
  void closeAfterEvaluationTimeIgnored() {
    Transaction open = margin(1, TransactionType.ACCUMULATE, null, 10, EVAL_TIME.minusDays(30));
    Transaction close = margin(2, TransactionType.REDUCE, 1, 10, EVAL_TIME.plusDays(5));
    assertThat(SecurityMarginUnitsCheck.getOpenPositionsAt(List.of(open, close), EVAL_TIME)).containsExactly(open);
  }

  @Test
  @DisplayName("An opening position after the evaluation time is not open yet")
  void openAfterEvaluationTime() {
    Transaction open = margin(1, TransactionType.ACCUMULATE, null, 10, EVAL_TIME.plusDays(1));
    assertThat(SecurityMarginUnitsCheck.getOpenPositionsAt(List.of(open), EVAL_TIME)).isEmpty();
  }

  @Test
  @DisplayName("Finance cost transactions neither open nor close a position")
  void financeCostIgnored() {
    Transaction open = margin(1, TransactionType.ACCUMULATE, null, 10, EVAL_TIME.minusDays(30));
    Transaction financeCost = margin(2, TransactionType.FINANCE_COST, 1, 30, EVAL_TIME.minusDays(10));
    assertThat(SecurityMarginUnitsCheck.getOpenPositionsAt(List.of(open, financeCost), EVAL_TIME))
        .containsExactly(open);
  }

  @Test
  @DisplayName("Two open positions at the same time are both returned (ambiguous for the import)")
  void twoOpenPositions() {
    Transaction open1 = margin(1, TransactionType.ACCUMULATE, null, 10, EVAL_TIME.minusDays(30));
    Transaction open2 = margin(2, TransactionType.REDUCE, null, 5, EVAL_TIME.minusDays(20));
    assertThat(SecurityMarginUnitsCheck.getOpenPositionsAt(List.of(open1, open2), EVAL_TIME))
        .containsExactlyInAnyOrder(open1, open2);
  }
}
