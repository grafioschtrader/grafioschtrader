package grafioschtrader.exportcsv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Transaction;
import grafioschtrader.exportcsv.CashTransferRelinkService.MatchResult;
import grafioschtrader.exportcsv.CashTransferRelinkService.TransferPair;
import grafioschtrader.types.TransactionType;

/**
 * Tests the pure matching rules of the cash transfer relink: minute bucketing, amount/currency conditions and the
 * one-to-one unambiguity guard. Runs without Spring, repositories or database.
 */
class CashTransferRelinkServiceTest {

  private static final LocalDateTime TIME = LocalDateTime.of(2026, 3, 9, 14, 5, 0);

  @Test
  @DisplayName("Same-currency pair with equal absolute amounts is linked")
  void sameCurrencyPairMatches() {
    Transaction w = transaction(1, TransactionType.WITHDRAWAL, 10, "CHF", -1500.0, TIME);
    Transaction d = transaction(2, TransactionType.DEPOSIT, 11, "CHF", 1500.0, TIME);
    MatchResult result = CashTransferRelinkService.findUnambiguousPairs(List.of(w, d));
    assertEquals(1, result.pairs().size());
    assertPair(result.pairs().get(0), 1, 2);
    assertEquals(0, result.ambiguous());
  }

  @Test
  @DisplayName("Same-currency sides with different amounts do not match")
  void sameCurrencyDifferentAmountNoMatch() {
    Transaction w = transaction(1, TransactionType.WITHDRAWAL, 10, "CHF", -1500.0, TIME);
    Transaction d = transaction(2, TransactionType.DEPOSIT, 11, "CHF", 1499.5, TIME);
    MatchResult result = CashTransferRelinkService.findUnambiguousPairs(List.of(w, d));
    assertTrue(result.pairs().isEmpty());
    assertEquals(0, result.ambiguous());
  }

  @Test
  @DisplayName("Cross-currency pair in the same minute is linked")
  void crossCurrencyPairMatches() {
    Transaction w = transaction(1, TransactionType.WITHDRAWAL, 10, "CHF", -1000.0, TIME);
    Transaction d = transaction(2, TransactionType.DEPOSIT, 11, "EUR", 930.0, TIME);
    MatchResult result = CashTransferRelinkService.findUnambiguousPairs(List.of(w, d));
    assertEquals(1, result.pairs().size());
    assertPair(result.pairs().get(0), 1, 2);
  }

  @Test
  @DisplayName("Different minute means no match")
  void differentMinuteNoMatch() {
    Transaction w = transaction(1, TransactionType.WITHDRAWAL, 10, "CHF", -1500.0, TIME);
    Transaction d = transaction(2, TransactionType.DEPOSIT, 11, "CHF", 1500.0, TIME.plusMinutes(1));
    MatchResult result = CashTransferRelinkService.findUnambiguousPairs(List.of(w, d));
    assertTrue(result.pairs().isEmpty());
  }

  @Test
  @DisplayName("Seconds within the same minute are ignored by the bucketing")
  void secondsIgnoredInBucketing() {
    Transaction w = transaction(1, TransactionType.WITHDRAWAL, 10, "CHF", -1500.0, TIME.plusSeconds(10));
    Transaction d = transaction(2, TransactionType.DEPOSIT, 11, "CHF", 1500.0, TIME.plusSeconds(45));
    MatchResult result = CashTransferRelinkService.findUnambiguousPairs(List.of(w, d));
    assertEquals(1, result.pairs().size());
  }

  @Test
  @DisplayName("Two identical transfers in the same minute are ambiguous and skipped")
  void ambiguousDuoSkipped() {
    Transaction w1 = transaction(1, TransactionType.WITHDRAWAL, 10, "CHF", -1500.0, TIME);
    Transaction w2 = transaction(2, TransactionType.WITHDRAWAL, 12, "CHF", -1500.0, TIME);
    Transaction d1 = transaction(3, TransactionType.DEPOSIT, 11, "CHF", 1500.0, TIME);
    Transaction d2 = transaction(4, TransactionType.DEPOSIT, 13, "CHF", 1500.0, TIME);
    MatchResult result = CashTransferRelinkService.findUnambiguousPairs(List.of(w1, w2, d1, d2));
    assertTrue(result.pairs().isEmpty());
    assertEquals(4, result.ambiguous());
  }

  @Test
  @DisplayName("Sides on the same cash account never match")
  void sameCashaccountNoMatch() {
    Transaction w = transaction(1, TransactionType.WITHDRAWAL, 10, "CHF", -1500.0, TIME);
    Transaction d = transaction(2, TransactionType.DEPOSIT, 10, "CHF", 1500.0, TIME);
    MatchResult result = CashTransferRelinkService.findUnambiguousPairs(List.of(w, d));
    assertTrue(result.pairs().isEmpty());
  }

  @Test
  @DisplayName("A distinct second pair in the same minute stays unambiguous through the amount condition")
  void distinctAmountsInSameMinuteBothLinked() {
    Transaction w1 = transaction(1, TransactionType.WITHDRAWAL, 10, "CHF", -1500.0, TIME);
    Transaction d1 = transaction(2, TransactionType.DEPOSIT, 11, "CHF", 1500.0, TIME);
    Transaction w2 = transaction(3, TransactionType.WITHDRAWAL, 12, "CHF", -77.25, TIME);
    Transaction d2 = transaction(4, TransactionType.DEPOSIT, 13, "CHF", 77.25, TIME);
    MatchResult result = CashTransferRelinkService.findUnambiguousPairs(List.of(w1, d1, w2, d2));
    assertEquals(2, result.pairs().size());
    assertEquals(0, result.ambiguous());
  }

  @Test
  @DisplayName("Cross-currency withdrawal facing two possible deposits is ambiguous")
  void crossCurrencyAmbiguousSkipped() {
    Transaction w = transaction(1, TransactionType.WITHDRAWAL, 10, "CHF", -1000.0, TIME);
    Transaction d1 = transaction(2, TransactionType.DEPOSIT, 11, "EUR", 930.0, TIME);
    Transaction d2 = transaction(3, TransactionType.DEPOSIT, 12, "USD", 1080.0, TIME);
    MatchResult result = CashTransferRelinkService.findUnambiguousPairs(List.of(w, d1, d2));
    assertTrue(result.pairs().isEmpty());
    assertEquals(3, result.ambiguous());
  }

  private void assertPair(TransferPair pair, int idWithdrawal, int idDeposit) {
    assertEquals(idWithdrawal, pair.withdrawal().getIdTransaction());
    assertEquals(idDeposit, pair.deposit().getIdTransaction());
  }

  private Transaction transaction(int idTransaction, TransactionType type, int idCashaccount, String currency,
      double amount, LocalDateTime time) {
    Transaction t = new Transaction();
    t.setIdTransaction(idTransaction);
    t.setTransactionType(type);
    Cashaccount cashaccount = new Cashaccount();
    cashaccount.setIdSecuritycashAccount(idCashaccount);
    cashaccount.setCurrency(currency);
    t.setCashaccount(cashaccount);
    t.setCashaccountAmount(amount);
    t.setTransactionTime(time);
    return t;
  }
}
