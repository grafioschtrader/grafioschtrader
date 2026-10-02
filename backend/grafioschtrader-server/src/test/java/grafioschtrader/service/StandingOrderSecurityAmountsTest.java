package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.service.StandingOrderExecutionService.SecurityOrderAmounts;
import grafioschtrader.types.TransactionType;

/**
 * The calculation of units, costs and cash account amount of a security standing order, which the daily execution and
 * Historical Replay share. The expected values are those the calculation produced while it was still written inline in
 * the daily execution.
 */
class StandingOrderSecurityAmountsTest {

  private static final double EPS = 1e-9;

  @Test
  @DisplayName("Unit mode books the fixed units and adds fixed and formula costs to a purchase")
  void unitModePurchase() {
    SecurityOrderAmounts amounts = StandingOrderExecutionService.securityOrderAmounts(10d, null, false, false, null,
        "a * 0.001", 5d, null, TransactionType.ACCUMULATE, 50, null, "CHF", "CHF");

    assertThat(amounts.units()).isEqualTo(10);
    assertThat(amounts.taxCost()).isCloseTo(0.5, within(EPS));
    assertThat(amounts.transactionCost()).isEqualTo(5);
    assertThat(amounts.cashaccountAmount()).isCloseTo(-505.5, within(EPS));
  }

  @Test
  @DisplayName("Gross amount mode pays the costs out of the amount and rounds the units down")
  void grossAmountMode() {
    SecurityOrderAmounts amounts = StandingOrderExecutionService.securityOrderAmounts(null, 1000d, true, false, null,
        null, 10d, null, TransactionType.ACCUMULATE, 32, null, "CHF", "CHF");

    // (1000 - 10) / 32 = 30.94 -> 30 whole units
    assertThat(amounts.units()).isEqualTo(30);
    assertThat(amounts.taxCost()).isZero();
    assertThat(amounts.cashaccountAmount()).isCloseTo(-(30 * 32 + 10), within(EPS));
  }

  @Test
  @DisplayName("Net amount mode invests the whole amount and evaluates the cost formula on the booked units")
  void netAmountModeWithFractionalUnits() {
    SecurityOrderAmounts amounts = StandingOrderExecutionService.securityOrderAmounts(null, 1000d, false, true, null,
        null, null, "u * q * 0.01", TransactionType.ACCUMULATE, 30, null, "CHF", "CHF");

    assertThat(amounts.units()).isCloseTo(1000d / 30, within(EPS));
    assertThat(amounts.transactionCost()).isCloseTo(10, within(1e-6));
    assertThat(amounts.cashaccountAmount()).isCloseTo(-1010, within(1e-6));
  }

  @Test
  @DisplayName("A sale deducts the costs and converts the proceeds into the cash account currency")
  void saleWithExchangeRate() {
    SecurityOrderAmounts amounts = StandingOrderExecutionService.securityOrderAmounts(10d, null, false, false, 2d,
        null, 5d, null, TransactionType.REDUCE, 100, 0.9, "USD", "CHF");

    assertThat(amounts.cashaccountAmount())
        .isCloseTo(DataBusinessHelper.divideMultiplyExchangeRate(1000 - 2 - 5, 0.9, "USD", "CHF"), within(EPS));
    assertThat(amounts.cashaccountAmount()).isNotCloseTo(993, within(EPS));
  }

  @Test
  @DisplayName("Gross amount mode with an exchange rate converts the whole debit")
  void grossAmountModeWithExchangeRate() {
    SecurityOrderAmounts amounts = StandingOrderExecutionService.securityOrderAmounts(null, 500d, true, true, null,
        null, 5d, null, TransactionType.ACCUMULATE, 99, 1.1, "EUR", "CHF");

    assertThat(amounts.units()).isCloseTo(495d / 99, within(EPS));
    assertThat(amounts.cashaccountAmount())
        .isCloseTo(DataBusinessHelper.divideMultiplyExchangeRate(-500, 1.1, "EUR", "CHF"), within(1e-6));
  }

  @Test
  @DisplayName("An amount that buys no whole unit is refused with the zero-units key")
  void zeroUnitsRefused() {
    assertThatThrownBy(() -> StandingOrderExecutionService.securityOrderAmounts(null, 50d, false, false, null, null,
        null, null, TransactionType.ACCUMULATE, 120, null, "CHF", "CHF"))
        .isInstanceOf(StandingOrderBusinessException.class)
        .hasMessage(StandingOrderExecutionService.ZERO_UNITS_KEY);
  }
}
