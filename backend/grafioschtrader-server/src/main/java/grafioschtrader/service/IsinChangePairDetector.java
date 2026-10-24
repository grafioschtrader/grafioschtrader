package grafioschtrader.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import grafioschtrader.entities.SecurityAction;
import grafioschtrader.entities.Transaction;
import grafioschtrader.types.TransactionType;

/**
 * Finds the securities accounts in which a tenant has already carried out an ISIN change before applying it: a sale
 * of the whole position of the old security and a purchase of the new one close to the action date. Such a pair was
 * entered by the user, or it was created by the system in an application whose records were lost. Applying the ISIN
 * change must then not book a second pair.
 *
 * <p>
 * A pair consists of a sale of the old security that brings its position in the securities account to zero and a
 * purchase of the new security in the same securities account, both within {@value #WINDOW_DAYS} days of the action
 * date. The purchased units must match the sold units converted by the split ratio of the ISIN change; a deviation of
 * less than {@value #UNITS_TOLERANCE} unit is accepted, because banks settle fractions in cash.
 * </p>
 */
public final class IsinChangePairDetector {

  /** Calendar days before and after the action date in which a pair is searched. */
  static final int WINDOW_DAYS = 10;
  /** Accepted deviation of the purchased units from the converted sold units. */
  static final double UNITS_TOLERANCE = 1.0;
  /** Note of the transactions an ISIN change creates. */
  static final String SYSTEM_CREATED = "System-Created";

  /**
   * A sale of the old and a purchase of the new security that already carried out the ISIN change in one securities
   * account.
   *
   * @param sell          sale of the whole position of the old security
   * @param buy           purchase of the new security
   * @param systemCreated true when both were created by an ISIN change whose application no longer exists
   */
  public record PerformedPair(Transaction sell, Transaction buy, boolean systemCreated) {
  }

  private IsinChangePairDetector() {
  }

  /**
   * Finds the already performed pairs of a tenant, at most one per securities account.
   *
   * @param action          the ISIN change; its date and split ratio are read
   * @param oldTransactions all transactions of the tenant on the old security
   * @param newTransactions all transactions of the tenant on the new security
   * @return the pairs found, empty when the ISIN change has not been carried out yet
   */
  public static List<PerformedPair> detect(SecurityAction action, List<Transaction> oldTransactions,
      List<Transaction> newTransactions) {
    double ratio = action.getFromFactor() != null && action.getToFactor() != null
        ? action.getToFactor() / (double) action.getFromFactor()
        : 1.0;
    Map<Integer, List<Transaction>> oldByAccount = oldTransactions.stream()
        .filter(t -> t.getIdSecurityaccount() != null)
        .collect(Collectors.groupingBy(Transaction::getIdSecurityaccount));
    Set<Transaction> usedBuys = new HashSet<>();
    List<PerformedPair> pairs = new ArrayList<>();
    for (Map.Entry<Integer, List<Transaction>> entry : oldByAccount.entrySet()) {
      findClosingSale(entry.getValue(), action.getActionDate()).ifPresent(sell -> findPurchase(entry.getKey(), sell,
          ratio, action.getActionDate(), newTransactions, usedBuys).ifPresent(buy -> {
            usedBuys.add(buy);
            pairs.add(new PerformedPair(sell, buy, isOrphanedSystemTransaction(sell) && isOrphanedSystemTransaction(buy)));
          }));
    }
    return pairs;
  }

  /**
   * The sale within the window after which the position of the old security in the securities account is zero; the
   * last such sale when there are several.
   */
  private static Optional<Transaction> findClosingSale(List<Transaction> accountTransactions, LocalDate actionDate) {
    List<Transaction> sorted = accountTransactions.stream().sorted(Comparator.comparing(Transaction::getTransactionTime))
        .toList();
    double position = 0;
    Transaction closingSale = null;
    for (Transaction transaction : sorted) {
      if (transaction.getTransactionType() == TransactionType.ACCUMULATE) {
        position += transaction.getUnits();
      } else if (transaction.getTransactionType() == TransactionType.REDUCE) {
        position -= transaction.getUnits();
        if (Math.abs(position) < 1e-9 && withinWindow(transaction.getTransactionDate(), actionDate)) {
          closingSale = transaction;
        }
      }
    }
    return Optional.ofNullable(closingSale);
  }

  /**
   * The purchase of the new security in the same securities account matching the sale; a system-created one before a
   * manual one, then the one closest to the sale.
   */
  private static Optional<Transaction> findPurchase(Integer idSecurityaccount, Transaction sell, double ratio,
      LocalDate actionDate, List<Transaction> newTransactions, Set<Transaction> usedBuys) {
    double expectedUnits = sell.getUnits() * ratio;
    return newTransactions.stream()
        .filter(t -> t.getTransactionType() == TransactionType.ACCUMULATE
            && Objects.equals(t.getIdSecurityaccount(), idSecurityaccount) && !usedBuys.contains(t)
            && withinWindow(t.getTransactionDate(), actionDate)
            && Math.abs(t.getUnits() - expectedUnits) < UNITS_TOLERANCE)
        .min(Comparator.comparing((Transaction t) -> !isOrphanedSystemTransaction(t)).thenComparingLong(
            t -> Math.abs(ChronoUnit.DAYS.between(sell.getTransactionDate(), t.getTransactionDate()))));
  }

  private static boolean withinWindow(LocalDate date, LocalDate actionDate) {
    return Math.abs(ChronoUnit.DAYS.between(actionDate, date)) <= WINDOW_DAYS;
  }

  private static boolean isOrphanedSystemTransaction(Transaction transaction) {
    return SYSTEM_CREATED.equals(transaction.getNote()) && transaction.getIdSecurityActionApp() == null;
  }
}
