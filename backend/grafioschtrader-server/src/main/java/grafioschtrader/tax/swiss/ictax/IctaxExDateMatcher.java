package grafioschtrader.tax.swiss.ictax;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import grafioschtrader.entities.IctaxPayment;
import grafioschtrader.entities.IctaxSecurityTaxData;
import grafioschtrader.entities.Transaction;

/**
 * Matches dividend transactions to the official ex-date of an ICTax (Swiss Kursliste) payment so a missing
 * {@code Transaction.exDate} can be back-filled.
 *
 * <p>
 * A booking date ({@code tt_date}) rarely lines up exactly with the tax data: a transaction may be booked on the tax
 * {@code paymentDate}, on the {@code exDate} (with the payment up to ~two weeks later), or a few days off either end.
 * The matcher therefore measures the gap of the transaction date against the closed interval
 * {@code [exDate, paymentDate]} of each candidate payment and accepts the closest payment only when that gap is within
 * a tolerance. Genuine "no tax row for that distribution" cases (nearest rows weeks away) stay unmatched.
 * </p>
 *
 * <p>
 * Only payments carrying an {@code exDate} are eligible — a payment without an ex-date has nothing to assign. Multiple
 * payments sharing a {@code paymentDate} (a capital-gain / KEP split next to the regular coupon) carry the same
 * {@code exDate}, so the assigned value is unambiguous regardless of which split row wins.
 * </p>
 */
public class IctaxExDateMatcher {

  /**
   * Default maximum number of days the transaction date may sit outside the {@code [exDate, paymentDate]} interval and
   * still be accepted as a match. Derived from real data where genuine matches deviate at most a few days while true
   * gaps are several weeks.
   */
  public static final int DEFAULT_TOLERANCE_DAYS = 7;

  private IctaxExDateMatcher() {
  }

  /**
   * Determines the ex-date to assign to each given dividend transaction.
   *
   * @param dividendTxWithoutExDate dividend transactions that currently have no ex-date (caller must pre-filter)
   * @param taxData                 ICTax tax data rows (possibly several per ISIN when more than one upload covers the
   *                                year); their payments are the match candidates
   * @param toleranceDays           maximum allowed gap in days to the {@code [exDate, paymentDate]} interval
   * @return an insertion-ordered map of transaction to the ex-date that should be set on it; transactions without a
   *         confident match are omitted
   */
  public static Map<Transaction, LocalDate> assignExDates(List<Transaction> dividendTxWithoutExDate,
      List<IctaxSecurityTaxData> taxData, int toleranceDays) {
    Map<String, List<IctaxPayment>> paymentsByIsin = collectEligiblePaymentsByIsin(taxData);

    Map<Transaction, LocalDate> result = new LinkedHashMap<>();
    for (Transaction transaction : dividendTxWithoutExDate) {
      if (transaction.getSecurity() == null) {
        continue;
      }
      List<IctaxPayment> candidates = paymentsByIsin.get(transaction.getSecurity().getIsin());
      if (candidates == null) {
        continue;
      }
      IctaxPayment best = findBestPayment(transaction.getTransactionTime().toLocalDate(), candidates, toleranceDays);
      if (best != null) {
        result.put(transaction, best.getExDate());
      }
    }
    return result;
  }

  /**
   * Builds a lookup of eligible payments (those carrying both an ex-date and a payment date) grouped by ISIN, merging
   * the payments of every tax-data row of the same ISIN.
   */
  private static Map<String, List<IctaxPayment>> collectEligiblePaymentsByIsin(List<IctaxSecurityTaxData> taxData) {
    Map<String, List<IctaxPayment>> paymentsByIsin = new LinkedHashMap<>();
    for (IctaxSecurityTaxData data : taxData) {
      if (data.getPayments() == null) {
        continue;
      }
      for (IctaxPayment payment : data.getPayments()) {
        if (payment.getExDate() != null && payment.getPaymentDate() != null) {
          paymentsByIsin.computeIfAbsent(data.getIsin(), _ -> new ArrayList<>()).add(payment);
        }
      }
    }
    return paymentsByIsin;
  }

  /**
   * Picks the candidate payment whose {@code [exDate, paymentDate]} interval is closest to the transaction date,
   * breaking ties towards the payment whose payment date is nearest, and returns it only when within tolerance.
   */
  private static IctaxPayment findBestPayment(LocalDate txDate, List<IctaxPayment> candidates, int toleranceDays) {
    IctaxPayment best = null;
    long bestGap = Long.MAX_VALUE;
    long bestPaymentDistance = Long.MAX_VALUE;
    for (IctaxPayment payment : candidates) {
      long gap = gapToInterval(txDate, payment.getExDate(), payment.getPaymentDate());
      long paymentDistance = Math.abs(ChronoUnit.DAYS.between(txDate, payment.getPaymentDate()));
      if (gap < bestGap || (gap == bestGap && paymentDistance < bestPaymentDistance)) {
        best = payment;
        bestGap = gap;
        bestPaymentDistance = paymentDistance;
      }
    }
    return bestGap <= toleranceDays ? best : null;
  }

  /**
   * Returns the gap in days of {@code date} to the closed interval spanned by the two boundary dates: zero when inside,
   * otherwise the distance to the nearest boundary.
   */
  private static long gapToInterval(LocalDate date, LocalDate boundaryA, LocalDate boundaryB) {
    LocalDate lo = boundaryA.isBefore(boundaryB) ? boundaryA : boundaryB;
    LocalDate hi = boundaryA.isBefore(boundaryB) ? boundaryB : boundaryA;
    if (!date.isBefore(lo) && !date.isAfter(hi)) {
      return 0;
    }
    return date.isBefore(lo) ? ChronoUnit.DAYS.between(date, lo) : ChronoUnit.DAYS.between(hi, date);
  }
}
