package grafioschtrader.instrument;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.dto.ProposedMarginFinanceCost;
import grafioschtrader.entities.Transaction;
import grafioschtrader.repository.TransactionJpaRepository;
import grafioschtrader.types.TransactionType;

/** Pure unit regressions for financing proposals, without a Spring context or database access. */
@DisplayName("Margin financing unpaid calendar days")
class SecurityMarginFinanceCostTest {
  private static final LocalDate OPEN = LocalDate.of(2025, 4, 10);
  private static final LocalDate CLOSE = LocalDate.of(2026, 9, 16);

  @Test
  @DisplayName("Forex closure proposes 524 days even without a daily rate")
  void closedWithoutRate() {
    assertProposal(estimate(opening(OPEN, null), closing(CLOSE, 100000)), 524, CLOSE, 0);
  }

  @Test
  @DisplayName("A supplied daily rate estimates the amount for the same 524 days")
  void closedWithRate() {
    assertProposal(estimate(opening(OPEN, 2.0), closing(CLOSE, 100000)), 524, CLOSE, 1048);
  }

  @Test
  @DisplayName("Deduct previous financing days even when payment was booked after closure")
  void previousPayments() {
    assertProposal(estimate(opening(OPEN, null), closing(CLOSE, 100000), payment(CLOSE.plusDays(1), 100)), 424, CLOSE,
        0);
  }

  @Test
  @DisplayName("Fully paid and overpaid closed positions do not accrue additional days")
  void fullyPaid() {
    for (int paidDays : new int[] { 524, 525 }) {
      assertProposal(estimate(opening(OPEN, 2.0), closing(CLOSE, 100000), payment(CLOSE, paidDays)), 0, CLOSE, 0);
    }
  }

  @Test
  @DisplayName("A position opened and closed on the same date has zero financing days")
  void sameDay() {
    assertProposal(estimate(opening(OPEN, 2.0), closing(OPEN, 100000)), 0, OPEN, 0);
  }

  @Test
  @DisplayName("An open position without a daily rate accrues unpaid days through today")
  void stillOpen() {
    LocalDate today = LocalDate.now();
    assertProposal(estimate(opening(today.minusDays(10), null), payment(today.minusDays(3), 4)), 6, today, 0);
  }

  @Test
  @DisplayName("Partial closure retains unpaid days through today with proportional costs")
  void partiallyClosed() {
    LocalDate today = LocalDate.now();
    assertProposal(estimate(opening(today.minusDays(10), 2.0), closing(today.minusDays(5), 50000)), 10, today, 15);
  }

  @Test
  @DisplayName("Payments ending at a partial closure use the remaining position's daily cost")
  void paidThroughPartialClosure() {
    LocalDate today = LocalDate.now();
    assertProposal(
        estimate(opening(today.minusDays(10), 2.0), closing(today.minusDays(5), 50000), payment(today.minusDays(2), 5)),
        5, today, 5);
  }

  @Test
  @DisplayName("Missing tenant-visible history yields an empty proposal")
  void noHistory() {
    assertProposal(estimate(), 0, null, 0);
  }

  private ProposedMarginFinanceCost estimate(Transaction... transactions) {
    TransactionJpaRepository repository = mock(TransactionJpaRepository.class);
    when(repository.getMarginForIdTenantAndIdTransactionOrderByTransactionTime(1, 1))
        .thenReturn(Arrays.stream(transactions).sorted(Comparator.comparing(Transaction::getTransactionTime)).toList());
    return SecurityMarginUnitsCheck.getEstimatedFinanceCost(repository, 1, 1);
  }

  private void assertProposal(ProposedMarginFinanceCost proposal, int days, LocalDate until, double amount) {
    assertThat(proposal.daysToPay).isEqualTo(days);
    assertThat(proposal.untilDate).isEqualTo(until);
    assertThat(proposal.financeCost).isEqualTo(amount);
  }

  private Transaction opening(LocalDate date, Double rate) {
    Transaction transaction = transaction(date, TransactionType.ACCUMULATE, 100000);
    transaction.setIdTransaction(1);
    transaction.setConnectedIdTransaction(null);
    transaction.setAssetInvestmentValue1(rate);
    return transaction;
  }

  private Transaction closing(LocalDate date, double units) {
    return transaction(date, TransactionType.REDUCE, units);
  }

  private Transaction payment(LocalDate date, int days) {
    return transaction(date, TransactionType.FINANCE_COST, days);
  }

  private Transaction transaction(LocalDate date, TransactionType type, double units) {
    Transaction transaction = new Transaction();
    transaction.setTransactionTime(date.atTime(6, 20));
    transaction.setTransactionType(type);
    transaction.setUnits(units);
    transaction.setConnectedIdTransaction(1);
    return transaction;
  }
}
