package grafioschtrader.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.FromToCurrency;
import grafioschtrader.reportviews.FromToCurrencyWithDate;
import grafioschtrader.types.TransactionType;

class HoldCashaccountDepositCalculationTest {

  private static final LocalDate TRANSFER_DATE = LocalDate.of(2006, 5, 31);

  @Test
  void connectedTransferUsesTheBookedSideMatchingTheTargetCurrency() {
    Transaction withdrawal = transaction(1, 2, TransactionType.WITHDRAWAL, "CHF", -36563.07);
    Transaction deposit = transaction(2, 1, TransactionType.DEPOSIT, "USD", 30000.0);

    assertOppositeAmounts(withdrawal, deposit, "CHF", 36563.07, Map.of());
    assertOppositeAmounts(withdrawal, deposit, "USD", 30000.0, Map.of());
  }

  @Test
  void connectedTransferUsesWithdrawalAsStableFallbackForAThirdCurrency() {
    Transaction withdrawal = transaction(1, 2, TransactionType.WITHDRAWAL, "CHF", -36563.07);
    Transaction deposit = transaction(2, 1, TransactionType.DEPOSIT, "USD", 30000.0);
    Map<FromToCurrencyWithDate, Double> rates = Map.of(new FromToCurrencyWithDate("CHF", "EUR", TRANSFER_DATE), 0.95);

    assertOppositeAmounts(withdrawal, deposit, "EUR", 36563.07 * 0.95, rates);
  }

  private void assertOppositeAmounts(Transaction withdrawal, Transaction deposit, String mainCurrency,
      double expectedAbsoluteAmount, Map<FromToCurrencyWithDate, Double> rates) {
    double withdrawalAmount = HoldCashaccountDepositJpaRepositoryImpl.calculateConnectedTransferAmount(withdrawal,
        deposit, mainCurrency, rates, Map.<FromToCurrency, Currencypair>of()).amountMC;
    double depositAmount = HoldCashaccountDepositJpaRepositoryImpl.calculateConnectedTransferAmount(deposit, withdrawal,
        mainCurrency, rates, Map.<FromToCurrency, Currencypair>of()).amountMC;

    Assertions.assertThat(withdrawalAmount).isEqualTo(-expectedAbsoluteAmount);
    Assertions.assertThat(depositAmount).isEqualTo(expectedAbsoluteAmount);
  }

  private Transaction transaction(Integer id, Integer connectedId, TransactionType transactionType, String currency,
      double amount) {
    Transaction transaction = new Transaction(new Cashaccount(id + 10, currency), amount, transactionType,
        LocalDateTime.of(TRANSFER_DATE, LocalTime.NOON));
    transaction.setIdTransaction(id);
    transaction.setConnectedIdTransaction(connectedId);
    transaction.setIdCurrencypair(100);
    transaction.setCurrencyExRate(0.82050003);
    return transaction;
  }
}
