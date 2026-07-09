package grafioschtrader.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafiosch.exceptions.DataViolationException;
import grafioschtrader.types.SpecialInvestmentInstruments;
import grafioschtrader.types.TransactionType;

/**
 * Pure unit tests for the rounding-difference handling in {@link Transaction#validateCashaccountAmount}. The scenario
 * reproduces import position 37332: a dividend of 175 units at 1.2546 calculates to 219.555 which direct two-digit
 * rounding turns into 219.55, while the document booked 219.56.
 */
class TransactionRoundingDiffTest {

  private static final int CURRENCY_FRACTION = 2;

  private Transaction buildDividend(double bankAmount) {
    Assetclass assetclass = new Assetclass();
    assetclass.setSpecialInvestmentInstrument(SpecialInvestmentInstruments.DIRECT_INVESTMENT);
    Security security = new Security();
    security.setCurrency("USD");
    security.setAssetClass(assetclass);
    Cashaccount cashaccount = new Cashaccount(1, "USD");

    return new Transaction(10, cashaccount, security, bankAmount, 175.0, 1.2546, TransactionType.DIVIDEND, null, null,
        null, LocalDateTime.now(), null, null, null, Boolean.FALSE);
  }

  @Test
  @DisplayName("Without a tolerance or recorded difference the one-cent mismatch is rejected")
  void rejectsWhenNoTolerance() {
    Transaction transaction = buildDividend(219.56);
    assertThatThrownBy(() -> transaction.validateCashaccountAmount(null, CURRENCY_FRACTION))
        .isInstanceOf(DataViolationException.class);
  }

  @Test
  @DisplayName("A configured tolerance accepts the mismatch and records the rounding difference")
  void acceptsWithinToleranceAndRecordsDiff() {
    Transaction transaction = buildDividend(219.56);
    transaction.setAcceptableRoundingStep(0.01);
    transaction.validateCashaccountAmount(null, CURRENCY_FRACTION);
    // diff convention: round(calculated) - round(booked) = 219.55 - 219.56 = -0.01
    assertThat(transaction.getCashaccountRoundingDiff()).isCloseTo(-0.01, within(1e-9));
    assertThat(transaction.getCashaccountAmount()).isEqualTo(219.56);
  }

  @Test
  @DisplayName("A difference larger than the tolerance is still rejected")
  void rejectsWhenBeyondTolerance() {
    Transaction transaction = buildDividend(219.56);
    transaction.setAcceptableRoundingStep(0.001);
    assertThatThrownBy(() -> transaction.validateCashaccountAmount(null, CURRENCY_FRACTION))
        .isInstanceOf(DataViolationException.class);
  }

  @Test
  @DisplayName("An explicitly accepted difference is recorded even when it exceeds any tolerance")
  void explicitAcceptanceRecordsAnyDifference() {
    Transaction transaction = buildDividend(219.60); // recompute 219.55 -> 0.05 gap, beyond a cent tolerance
    transaction.setRoundingDiffExplicitlyAccepted(true);
    transaction.validateCashaccountAmount(null, CURRENCY_FRACTION);
    assertThat(transaction.getCashaccountRoundingDiff()).isCloseTo(-0.05, within(1e-9));
    assertThat(transaction.getCashaccountAmount()).isEqualTo(219.60);
  }

  @Test
  @DisplayName("A previously recorded rounding difference ties the calculated amount to the booked amount")
  void acceptsWithPreRecordedDiff() {
    Transaction transaction = buildDividend(219.56);
    transaction.setCashaccountRoundingDiff(-0.01);
    transaction.validateCashaccountAmount(null, CURRENCY_FRACTION);
    assertThat(transaction.getCashaccountAmount()).isEqualTo(219.56);
  }
}
