package grafioschtrader.connector.ictax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.entities.IctaxPayment;
import grafioschtrader.entities.IctaxSecurityTaxData;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Transaction;
import grafioschtrader.tax.swiss.ictax.IctaxExDateMatcher;
import grafioschtrader.types.TransactionType;

/**
 * Pure matcher tests (no Spring context) for {@link IctaxExDateMatcher}, using the date constellations observed for
 * id_tenant=7 / tax year 2025.
 */
class IctaxExDateMatcherTest {

  @Test
  @DisplayName("Exact, booked-on-ex-date and few-days-after-payment all resolve to the tax ex-date")
  void matchesWithinTolerance() {
    // Exact: tt_date == paymentDate.
    Transaction exact = divTx("CH0237935652", LocalDate.of(2025, 4, 15));
    // Booked on the ex-date; payment 13 days later -> transaction date lies inside [exDate, paymentDate].
    Transaction onExDate = divTx("IE00B988C465", LocalDate.of(2025, 4, 17));
    // A few days after the payment date.
    Transaction afterPayment = divTx("IE00B0M63177", LocalDate.of(2025, 12, 29));

    List<IctaxSecurityTaxData> taxData = List.of(
        taxData("CH0237935652", payment(LocalDate.of(2025, 4, 15), LocalDate.of(2025, 4, 11), false)),
        taxData("IE00B988C465", payment(LocalDate.of(2025, 4, 30), LocalDate.of(2025, 4, 17), false)),
        taxData("IE00B0M63177", payment(LocalDate.of(2025, 12, 24), LocalDate.of(2025, 12, 11), false)));

    Map<Transaction, LocalDate> result = IctaxExDateMatcher.assignExDates(List.of(exact, onExDate, afterPayment),
        taxData, IctaxExDateMatcher.DEFAULT_TOLERANCE_DAYS);

    assertEquals(LocalDate.of(2025, 4, 11), result.get(exact));
    assertEquals(LocalDate.of(2025, 4, 17), result.get(onExDate));
    assertEquals(LocalDate.of(2025, 12, 11), result.get(afterPayment));
  }

  @Test
  @DisplayName("A distribution with no tax row within tolerance stays unmatched")
  void leavesGenuineGapUnmatched() {
    // Monthly distributor with tax rows in May and July but a transaction booked in June.
    Transaction juneTx = divTx("IE00B9M04V95", LocalDate.of(2025, 6, 25));
    List<IctaxSecurityTaxData> taxData = List.of(taxData("IE00B9M04V95",
        payment(LocalDate.of(2025, 5, 29), LocalDate.of(2025, 5, 15), false),
        payment(LocalDate.of(2025, 7, 30), LocalDate.of(2025, 7, 17), false)));

    Map<Transaction, LocalDate> result = IctaxExDateMatcher.assignExDates(List.of(juneTx), taxData,
        IctaxExDateMatcher.DEFAULT_TOLERANCE_DAYS);

    assertFalse(result.containsKey(juneTx), "June transaction must not be matched to a May/July tax row");
  }

  @Test
  @DisplayName("Capital-gain / KEP split on the same date assigns the shared ex-date; nearest interval wins")
  void handlesCapitalGainSplitAndPicksNearest() {
    // Two transactions on the same pay date; tax data has the cap-gain split (same exDate) plus an unrelated
    // earlier coupon that must not win.
    Transaction t1 = divTx("CH0237935652", LocalDate.of(2025, 7, 17));
    Transaction t2 = divTx("CH0237935652", LocalDate.of(2025, 7, 17));
    List<IctaxSecurityTaxData> taxData = List.of(taxData("CH0237935652",
        payment(LocalDate.of(2025, 4, 15), LocalDate.of(2025, 4, 11), false),
        payment(LocalDate.of(2025, 7, 17), LocalDate.of(2025, 7, 15), true),
        payment(LocalDate.of(2025, 7, 17), LocalDate.of(2025, 7, 15), false)));

    Map<Transaction, LocalDate> result = IctaxExDateMatcher.assignExDates(List.of(t1, t2), taxData,
        IctaxExDateMatcher.DEFAULT_TOLERANCE_DAYS);

    assertEquals(LocalDate.of(2025, 7, 15), result.get(t1));
    assertEquals(LocalDate.of(2025, 7, 15), result.get(t2));
  }

  @Test
  @DisplayName("Transactions whose ISIN has no tax data are omitted")
  void omitsTransactionsWithoutTaxData() {
    Transaction noTaxData = divTx("US67066G1040", LocalDate.of(2025, 4, 2));
    Map<Transaction, LocalDate> result = IctaxExDateMatcher.assignExDates(List.of(noTaxData), List.of(),
        IctaxExDateMatcher.DEFAULT_TOLERANCE_DAYS);
    assertTrue(result.isEmpty());
  }

  private Transaction divTx(String isin, LocalDate transactionDate) {
    Security security = new Security();
    security.setIsin(isin);
    Transaction transaction = new Transaction();
    transaction.setSecuritycurrency(security);
    transaction.setTransactionTime(transactionDate.atStartOfDay());
    transaction.setTransactionType(TransactionType.DIVIDEND);
    return transaction;
  }

  private IctaxSecurityTaxData taxData(String isin, IctaxPayment... payments) {
    IctaxSecurityTaxData data = new IctaxSecurityTaxData();
    data.setIsin(isin);
    data.setPayments(new ArrayList<>(List.of(payments)));
    return data;
  }

  private IctaxPayment payment(LocalDate paymentDate, LocalDate exDate, boolean capitalGain) {
    IctaxPayment payment = new IctaxPayment();
    payment.setPaymentDate(paymentDate);
    payment.setExDate(exDate);
    payment.setCapitalGain(capitalGain);
    return payment;
  }
}
