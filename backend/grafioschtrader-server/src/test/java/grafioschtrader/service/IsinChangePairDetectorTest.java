package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.entities.SecurityAction;
import grafioschtrader.entities.Transaction;
import grafioschtrader.service.IsinChangePairDetector.PerformedPair;
import grafioschtrader.types.TransactionType;

/**
 * Recognition of an ISIN change a tenant has already carried out, modelled on the reverse splits of the ProShares
 * inverse ETFs: PSQ 1:5 on 2024-04-10 and SH 1:4 on 2024-11-07.
 */
class IsinChangePairDetectorTest {

  private static final int ACCOUNT = 25;
  private static final LocalDate PSQ_DATE = LocalDate.of(2024, 4, 9);
  private int nextId = 1;

  @Test
  @DisplayName("A pair of a lost application is found as system pair, with the units converted by the ratio")
  void orphanedSystemPair() {
    Transaction sell = trade(TransactionType.REDUCE, 1500, PSQ_DATE, IsinChangePairDetector.SYSTEM_CREATED);
    Transaction buy = trade(TransactionType.ACCUMULATE, 300, PSQ_DATE, IsinChangePairDetector.SYSTEM_CREATED);

    List<PerformedPair> pairs = IsinChangePairDetector.detect(action(PSQ_DATE, 5, 1),
        List.of(trade(TransactionType.ACCUMULATE, 1500, LocalDate.of(2023, 11, 7), null), sell), List.of(buy));

    assertThat(pairs).containsExactly(new PerformedPair(sell, buy, true));
  }

  @Test
  @DisplayName("A pair the user entered a few days after the action date is found, but not as system pair")
  void manualPairAfterActionDate() {
    Transaction sell = trade(TransactionType.REDUCE, 1500, PSQ_DATE.plusDays(3), "Bank statement");
    Transaction buy = trade(TransactionType.ACCUMULATE, 300, PSQ_DATE.plusDays(3), null);

    List<PerformedPair> pairs = IsinChangePairDetector.detect(action(PSQ_DATE, 5, 1),
        List.of(trade(TransactionType.ACCUMULATE, 1500, LocalDate.of(2023, 11, 7), null), sell), List.of(buy));

    assertThat(pairs).containsExactly(new PerformedPair(sell, buy, false));
  }

  @Test
  @DisplayName("A position sold long before and a later purchase of the successor are no pair")
  void soldLongBefore() {
    LocalDate shDate = LocalDate.of(2024, 11, 6);
    List<PerformedPair> pairs = IsinChangePairDetector.detect(action(shDate, 4, 1),
        List.of(trade(TransactionType.ACCUMULATE, 500, LocalDate.of(2008, 3, 20), null),
            trade(TransactionType.REDUCE, 500, LocalDate.of(2008, 9, 15), null)),
        List.of(trade(TransactionType.ACCUMULATE, 462, LocalDate.of(2025, 8, 8), null)));

    assertThat(pairs).isEmpty();
  }

  @Test
  @DisplayName("A purchase whose units do not match the split ratio is no pair")
  void unitsDoNotMatchRatio() {
    List<PerformedPair> pairs = IsinChangePairDetector.detect(action(PSQ_DATE, 5, 1),
        List.of(trade(TransactionType.ACCUMULATE, 1500, LocalDate.of(2023, 11, 7), null),
            trade(TransactionType.REDUCE, 1500, PSQ_DATE, null)),
        List.of(trade(TransactionType.ACCUMULATE, 1500, PSQ_DATE, null)));

    assertThat(pairs).isEmpty();
  }

  private static SecurityAction action(LocalDate actionDate, int fromFactor, int toFactor) {
    SecurityAction action = new SecurityAction();
    action.setActionDate(actionDate);
    action.setFromFactor(fromFactor);
    action.setToFactor(toFactor);
    return action;
  }

  private Transaction trade(TransactionType type, double units, LocalDate on, String note) {
    Transaction transaction = new Transaction();
    transaction.setIdTransaction(nextId++);
    transaction.setIdSecurityaccount(ACCOUNT);
    transaction.setTransactionType(type);
    transaction.setUnits(units);
    transaction.setTransactionTime(on.atTime(12, 0));
    transaction.setNote(note);
    return transaction;
  }
}
