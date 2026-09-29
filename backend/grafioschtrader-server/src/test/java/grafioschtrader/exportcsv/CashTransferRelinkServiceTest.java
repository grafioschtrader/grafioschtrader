package grafioschtrader.exportcsv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Transaction;
import grafioschtrader.exportcsv.CashTransferRelinkService.ExpectedRateLookup;
import grafioschtrader.exportcsv.CashTransferRelinkService.MatchResult;
import grafioschtrader.exportcsv.CashTransferRelinkService.TransferPair;
import grafioschtrader.types.TransactionType;

/**
 * Tests the pure matching rules of the cash transfer relink: minute bucketing, amount/currency conditions, the exchange
 * rate plausibility of cross-currency pairs and the one-to-one unambiguity guard. Runs without Spring, repositories or
 * database.
 */
class CashTransferRelinkServiceTest {

  private static final LocalDateTime TIME = LocalDateTime.of(2026, 3, 9, 14, 5, 0);

  /** A simulation books only dates, so every transfer of a day shares the midnight bucket. */
  private static final LocalDateTime SIMULATION_DAY = LocalDateTime.of(2020, 1, 7, 0, 0, 0);

  /** No close known: every cross-currency amount pair is plausible, as before the rate check existed. */
  private static final ExpectedRateLookup NO_CLOSE = (_, _, _) -> null;

  /** Closes of 2020-01-07 as stored in the price history (from currency first). */
  private static final Map<String, Double> CLOSES_2020_01_07 = Map.of("USDCHF", 0.9701, "EURCHF", 1.08428);

  private static final ExpectedRateLookup CLOSE_LOOKUP = (from, to, _) -> CLOSES_2020_01_07.get(from + to);

  @Test
  @DisplayName("Same-currency pair with equal absolute amounts is linked")
  void sameCurrencyPairMatches() {
    Transaction w = transaction(1, TransactionType.WITHDRAWAL, 10, "CHF", -1500.0, TIME);
    Transaction d = transaction(2, TransactionType.DEPOSIT, 11, "CHF", 1500.0, TIME);
    MatchResult result = CashTransferRelinkService.findUnambiguousPairs(List.of(w, d), NO_CLOSE);
    assertEquals(1, result.pairs().size());
    assertPair(result.pairs().get(0), 1, 2);
    assertEquals(0, result.ambiguous());
  }

  @Test
  @DisplayName("Same-currency sides with different amounts do not match")
  void sameCurrencyDifferentAmountNoMatch() {
    Transaction w = transaction(1, TransactionType.WITHDRAWAL, 10, "CHF", -1500.0, TIME);
    Transaction d = transaction(2, TransactionType.DEPOSIT, 11, "CHF", 1499.5, TIME);
    MatchResult result = CashTransferRelinkService.findUnambiguousPairs(List.of(w, d), NO_CLOSE);
    assertTrue(result.pairs().isEmpty());
    assertEquals(0, result.ambiguous());
  }

  @Test
  @DisplayName("Cross-currency pair in the same minute is linked")
  void crossCurrencyPairMatches() {
    Transaction w = transaction(1, TransactionType.WITHDRAWAL, 10, "CHF", -1000.0, TIME);
    Transaction d = transaction(2, TransactionType.DEPOSIT, 11, "EUR", 930.0, TIME);
    MatchResult result = CashTransferRelinkService.findUnambiguousPairs(List.of(w, d), NO_CLOSE);
    assertEquals(1, result.pairs().size());
    assertPair(result.pairs().get(0), 1, 2);
  }

  @Test
  @DisplayName("Different minute means no match")
  void differentMinuteNoMatch() {
    Transaction w = transaction(1, TransactionType.WITHDRAWAL, 10, "CHF", -1500.0, TIME);
    Transaction d = transaction(2, TransactionType.DEPOSIT, 11, "CHF", 1500.0, TIME.plusMinutes(1));
    MatchResult result = CashTransferRelinkService.findUnambiguousPairs(List.of(w, d), NO_CLOSE);
    assertTrue(result.pairs().isEmpty());
  }

  @Test
  @DisplayName("Seconds within the same minute are ignored by the bucketing")
  void secondsIgnoredInBucketing() {
    Transaction w = transaction(1, TransactionType.WITHDRAWAL, 10, "CHF", -1500.0, TIME.plusSeconds(10));
    Transaction d = transaction(2, TransactionType.DEPOSIT, 11, "CHF", 1500.0, TIME.plusSeconds(45));
    MatchResult result = CashTransferRelinkService.findUnambiguousPairs(List.of(w, d), NO_CLOSE);
    assertEquals(1, result.pairs().size());
  }

  @Test
  @DisplayName("Two identical transfers in the same minute are ambiguous and skipped")
  void ambiguousDuoSkipped() {
    Transaction w1 = transaction(1, TransactionType.WITHDRAWAL, 10, "CHF", -1500.0, TIME);
    Transaction w2 = transaction(2, TransactionType.WITHDRAWAL, 12, "CHF", -1500.0, TIME);
    Transaction d1 = transaction(3, TransactionType.DEPOSIT, 11, "CHF", 1500.0, TIME);
    Transaction d2 = transaction(4, TransactionType.DEPOSIT, 13, "CHF", 1500.0, TIME);
    MatchResult result = CashTransferRelinkService.findUnambiguousPairs(List.of(w1, w2, d1, d2), NO_CLOSE);
    assertTrue(result.pairs().isEmpty());
    assertEquals(4, result.ambiguous());
  }

  @Test
  @DisplayName("Sides on the same cash account never match")
  void sameCashaccountNoMatch() {
    Transaction w = transaction(1, TransactionType.WITHDRAWAL, 10, "CHF", -1500.0, TIME);
    Transaction d = transaction(2, TransactionType.DEPOSIT, 10, "CHF", 1500.0, TIME);
    MatchResult result = CashTransferRelinkService.findUnambiguousPairs(List.of(w, d), NO_CLOSE);
    assertTrue(result.pairs().isEmpty());
  }

  @Test
  @DisplayName("A distinct second pair in the same minute stays unambiguous through the amount condition")
  void distinctAmountsInSameMinuteBothLinked() {
    Transaction w1 = transaction(1, TransactionType.WITHDRAWAL, 10, "CHF", -1500.0, TIME);
    Transaction d1 = transaction(2, TransactionType.DEPOSIT, 11, "CHF", 1500.0, TIME);
    Transaction w2 = transaction(3, TransactionType.WITHDRAWAL, 12, "CHF", -77.25, TIME);
    Transaction d2 = transaction(4, TransactionType.DEPOSIT, 13, "CHF", 77.25, TIME);
    MatchResult result = CashTransferRelinkService.findUnambiguousPairs(List.of(w1, d1, w2, d2), NO_CLOSE);
    assertEquals(2, result.pairs().size());
    assertEquals(0, result.ambiguous());
  }

  @Test
  @DisplayName("Cross-currency withdrawal facing two possible deposits is ambiguous")
  void crossCurrencyAmbiguousSkipped() {
    Transaction w = transaction(1, TransactionType.WITHDRAWAL, 10, "CHF", -1000.0, TIME);
    Transaction d1 = transaction(2, TransactionType.DEPOSIT, 11, "EUR", 930.0, TIME);
    Transaction d2 = transaction(3, TransactionType.DEPOSIT, 12, "USD", 1080.0, TIME);
    MatchResult result = CashTransferRelinkService.findUnambiguousPairs(List.of(w, d1, d2), NO_CLOSE);
    assertTrue(result.pairs().isEmpty());
    assertEquals(3, result.ambiguous());
  }

  @Test
  @DisplayName("Two cross-currency transfers on the same day are separated by the close")
  void crossCurrencySeparatedByClose() {
    Transaction wUsd = transaction(1, TransactionType.WITHDRAWAL, 10, "USD", -163.86, SIMULATION_DAY);
    Transaction wEur = transaction(2, TransactionType.WITHDRAWAL, 11, "EUR", -5934.90, SIMULATION_DAY);
    Transaction dSmall = transaction(3, TransactionType.DEPOSIT, 12, "CHF", 159.22, SIMULATION_DAY);
    Transaction dLarge = transaction(4, TransactionType.DEPOSIT, 12, "CHF", 6267.02, SIMULATION_DAY);
    List<Transaction> candidates = List.of(wUsd, wEur, dSmall, dLarge);

    assertEquals(4, CashTransferRelinkService.findUnambiguousPairs(candidates, NO_CLOSE).ambiguous());

    MatchResult result = CashTransferRelinkService.findUnambiguousPairs(candidates, CLOSE_LOOKUP);
    assertEquals(2, result.pairs().size());
    assertEquals(0, result.ambiguous());
    assertPair(pairOf(result, 1), 1, 3);
    assertPair(pairOf(result, 2), 2, 4);
  }

  @Test
  @DisplayName("A same-currency counterpart wins over a cross-currency one that is within the tolerance")
  void sameCurrencyStageBeforeCrossCurrency() {
    // EUR 32124.05 -> CHF 34225.81 implies 1.0654, within 8 % of 1.08428, yet 34225.81 is the CHF transfer's side.
    Transaction wChf = transaction(1, TransactionType.WITHDRAWAL, 10, "CHF", -34225.81, SIMULATION_DAY);
    Transaction wChfSmall = transaction(2, TransactionType.WITHDRAWAL, 10, "CHF", -3135.10, SIMULATION_DAY);
    Transaction wUsd = transaction(3, TransactionType.WITHDRAWAL, 11, "USD", -21420.68, SIMULATION_DAY);
    Transaction wEur = transaction(4, TransactionType.WITHDRAWAL, 12, "EUR", -32124.05, SIMULATION_DAY);
    Transaction dChf = transaction(5, TransactionType.DEPOSIT, 20, "CHF", 34225.81, SIMULATION_DAY);
    Transaction dChfSmall = transaction(6, TransactionType.DEPOSIT, 20, "CHF", 3135.10, SIMULATION_DAY);
    Transaction dFromUsd = transaction(7, TransactionType.DEPOSIT, 20, "CHF", 20780.20, SIMULATION_DAY);
    Transaction dFromEur = transaction(8, TransactionType.DEPOSIT, 20, "CHF", 34831.47, SIMULATION_DAY);

    MatchResult result = CashTransferRelinkService
        .findUnambiguousPairs(List.of(wChf, wChfSmall, wUsd, wEur, dChf, dChfSmall, dFromUsd, dFromEur), CLOSE_LOOKUP);
    assertEquals(4, result.pairs().size());
    assertEquals(0, result.ambiguous());
    assertPair(pairOf(result, 1), 1, 5);
    assertPair(pairOf(result, 2), 2, 6);
    assertPair(pairOf(result, 3), 3, 7);
    assertPair(pairOf(result, 4), 4, 8);
  }

  @Test
  @DisplayName("A cross-currency pair whose implied rate is outside the tolerance is no candidate")
  void crossCurrencyOutsideToleranceNoMatch() {
    // 20000 / 21420.68 = 0.9337, 3.8 % below 0.9701 -> still a candidate; 18000 / 21420.68 = 0.8403 -> 13 % below.
    Transaction w = transaction(1, TransactionType.WITHDRAWAL, 10, "USD", -21420.68, SIMULATION_DAY);
    Transaction dInside = transaction(2, TransactionType.DEPOSIT, 11, "CHF", 20000.0, SIMULATION_DAY);
    Transaction dOutside = transaction(3, TransactionType.DEPOSIT, 11, "CHF", 18000.0, SIMULATION_DAY);

    MatchResult inside = CashTransferRelinkService.findUnambiguousPairs(List.of(w, dInside), CLOSE_LOOKUP);
    assertEquals(1, inside.pairs().size());

    MatchResult outside = CashTransferRelinkService.findUnambiguousPairs(List.of(w, dOutside), CLOSE_LOOKUP);
    assertTrue(outside.pairs().isEmpty());
    assertEquals(0, outside.ambiguous());
  }

  private TransferPair pairOf(MatchResult result, int idWithdrawal) {
    return result.pairs().stream().filter(p -> p.withdrawal().getIdTransaction() == idWithdrawal).findFirst()
        .orElseThrow();
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
