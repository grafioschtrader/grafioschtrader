package grafioschtrader.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests of the balance projection behind the overdraft checks of creating, updating and deleting a transaction.
 *
 * <p>
 * The projection starts from the balance valid on the transaction date, not the balance strictly before it. The row of
 * the transaction date already holds the bookings of that day, including a transaction that is being deleted, so the
 * balance before the date must not be charged with the change as well.
 * </p>
 */
@DisplayName("Overdraft check balance projection")
class TransactionOverdraftProjectionTest {

  /**
   * The account was empty before the day of a deposit of 346.13, which is its latest booking. Deleting it returns the
   * account to zero. The former projection charged the empty balance before the date with the deletion and reported
   * -346.13.
   */
  @Test
  @DisplayName("Deleting the latest deposit into an empty account returns it to zero")
  void deleteLatestDepositOfEmptyAccount() {
    assertThat(project(346.13, 346.13, -346.13)).isZero();
  }

  @Test
  @DisplayName("Deleting a deposit that later withdrawals depend on still fails")
  void deleteDepositNeededLaterFails() {
    // Balance 1000 on the deposit day, later withdrawals bring it down to 200.
    assertThat(project(1000.0, 200.0, -1000.0)).isNegative();
  }

  @Test
  @DisplayName("A withdrawal covered by a deposit of the same day is accepted")
  void withdrawalCoveredBySameDayDeposit() {
    // Empty before the day, the row of the day already holds the deposit of 1000.
    assertThat(project(1000.0, 1000.0, -500.0)).isEqualTo(500.0);
  }

  @Test
  @DisplayName("A withdrawal on a day without a row is checked against the earlier balance")
  void withdrawalOnDayWithoutRow() {
    // The row valid on the date is the earlier balance of 300; no rows on or after the date.
    assertThat(project(300.0, null, -500.0)).isEqualTo(-200.0);
  }

  @Test
  @DisplayName("A backdated withdrawal is checked against the lowest later balance")
  void backdatedWithdrawalUsesLowestLaterBalance() {
    assertThat(project(5000.0, 100.0, -500.0)).isEqualTo(-400.0);
  }

  @Test
  @DisplayName("An account without any row starts from zero")
  void accountWithoutRows() {
    assertThat(project(null, null, -1.0)).isEqualTo(-1.0);
  }

  private static double project(Double balanceOnDate, Double minFromDate, double delta) {
    return TransactionJpaRepositoryImpl.projectMinBalance(balanceOnDate, minFromDate, delta);
  }
}
