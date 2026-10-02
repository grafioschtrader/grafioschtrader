package grafioschtrader.report.pdf;

import static grafioschtrader.types.TransactionType.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.TransactionType;

/** Hand-calculated booking fixtures, independent of the PDF renderer and of persisted production data. */
class BookingDataTest {
  static final LocalDate FROM = LocalDate.of(2025, 1, 2), BOOKING = LocalDate.of(2025, 6, 12),
      TO = LocalDate.of(2025, 12, 31);

  static Security security(int id, String name, String currency) {
    return HoldingsReportTest.position(id, name, AssetclassType.EQUITIES, currency, 0).getSecurity();
  }

  static Transaction booking(int id, TransactionType type, Security security, String currency, double amount) {
    var t = new Transaction();
    t.setIdTransaction(id);
    t.setIdTenant(7);
    t.setTransactionTime(BOOKING.atTime(12, 0).plusMinutes(id));
    ReflectionTestUtils.setField(t, "transactionDate", BOOKING);
    t.setTransactionType(type);
    t.setSecuritycurrency(security);
    var account = new Cashaccount();
    account.setName("Account " + currency);
    account.setCurrency(currency);
    t.setCashaccount(account);
    t.setCashaccountAmount(amount);
    if (security != null) {
      t.setUnits(10.0);
      t.setQuotation(100.0);
    }
    return t;
  }

  static DateTransactionCurrencypairMap rates(boolean closingRate) {
    return new DateTransactionCurrencypairMap("CHF", TO,
        List.of(new Object[] { BOOKING, "USD", .8 }, new Object[] { TO, "USD", .9 },
            new Object[] { BOOKING, "EUR", .95 }, new Object[] { TO, "EUR", .98 }),
        List.of(), true, closingRate, TO.plusDays(20));
  }

  static HoldingsData holdings(int id, String name, double value) {
    return new HoldingsData(List.of(HoldingsReportTest.position(id, name, AssetclassType.EQUITIES, "CHF", value)),
        Map.of());
  }

  @Test
  @DisplayName("Closed positions retain net income and costs once; withholding tax follows the tenant setting")
  void closedPosition() {
    var s = security(1, "Closed position", "CHF");
    var buy = booking(1, ACCUMULATE, s, "CHF", -1020);
    buy.setTransactionCost(15.0);
    buy.setTaxCost(5.0);
    var sell = booking(2, REDUCE, s, "CHF", 1180);
    sell.setTransactionCost(15.0);
    sell.setTaxCost(5.0);
    var dividend = booking(3, DIVIDEND, s, "CHF", 70);
    dividend.setTaxCost(30.0);
    var financing = booking(4, FINANCE_COST, s, "CHF", -10);
    var credit = booking(5, FINANCE_COST, s, "CHF", 2);
    var fees = booking(6, FEE, null, "CHF", -12);
    var interest = booking(7, INTEREST_CASHACCOUNT, null, "CHF", 3);
    var data = BookingData.calculate(List.of(interest, dividend, buy, sell, financing, credit, fees), null, null,
        rates(false), false);
    var p = data.positions().getFirst();
    assertEquals(222, p.contribution()); // -1020 + 1180 + 70 - 10 + 2
    assertEquals(70, p.income());
    assertEquals(-30, p.withholdingTax());
    assertEquals(-30, p.transactionCosts());
    assertEquals(-10, p.transactionTaxes());
    assertEquals(-8, p.financing());
    assertEquals(60, data.recordedCosts()); // 30 + 10 + 12 + 8
    assertEquals(213, data.attributedGain());
    assertEquals(buy, data.transactions().getFirst().transaction());
    var withoutTax = BookingData.calculate(List.of(buy, sell, dividend, financing, credit), null, null, rates(false),
        true);
    assertEquals(252, withoutTax.positions().getFirst().contribution());
    assertEquals(100, withoutTax.positions().getFirst().income());
    // The added-back tax was never credited, so the reconciliation with the performance stays on the cash basis.
    assertEquals(30, withoutTax.addedBackTax());
    assertEquals(222, withoutTax.attributedGain());
  }

  @Test
  @DisplayName("Both fee conversion settings match signed account cash while security income stays at booking rates")
  void foreignCurrency() {
    var s = security(1, "USD security", "USD");
    var fee = booking(1, FEE, null, "USD", -10);
    var interest = booking(2, INTEREST_CASHACCOUNT, null, "USD", -2);
    var dividend = booking(3, DIVIDEND, s, "USD", 70);
    dividend.setTaxCost(30.0);
    for (boolean closing : List.of(false, true)) {
      var rates = rates(closing);
      var data = BookingData.calculate(List.of(fee, interest, dividend), null, null, rates, false);
      assertEquals(closing ? 9 : 8, data.fees(), 1e-9);
      assertEquals(fee.getFeeMC(rates), data.fees());
      assertEquals(closing ? -1.8 : -1.6, data.interest(), 1e-9);
      assertEquals(56, data.positions().getFirst().income(), 1e-9);
      assertEquals(-24, data.positions().getFirst().withholdingTax(), 1e-9);
    }
    // A CHF instrument on a USD account still requires cash currency conversion, not a spurious rate of 1.
    var chf = booking(4, DIVIDEND, security(2, "CHF on USD", "CHF"), "USD", 100);
    chf.setTaxCost(10.0);
    var data = BookingData.calculate(List.of(chf), null, null, rates(false), false);
    assertEquals(80, data.positions().getFirst().income());
    assertEquals(-10, data.positions().getFirst().withholdingTax());
  }

  @Test
  @DisplayName("No period booking means no period dividend; margin contribution uses equity rather than exposure")
  void valuationOnly() {
    var opening = holdings(1, "Margin", 10);
    var closing = holdings(1, "Margin", 35);
    opening.positions().getFirst().valueSecurityMC = 10000;
    closing.positions().getFirst().valueSecurityMC = 14000;
    opening.positions().getFirst().gainLossCurrencyMC = 80;
    closing.positions().getFirst().gainLossCurrencyMC = 120;
    var data = BookingData.calculate(List.of(), opening, closing, rates(false), false);
    assertEquals(25, data.positions().getFirst().contribution());
    assertEquals(0, data.positions().getFirst().income());
    assertEquals(25, data.attributedGain());
    opening.positions().getFirst().priceMissing = true;
    assertTrue(Double.isNaN(
        BookingData.calculate(List.of(), opening, closing, rates(false), false).positions().getFirst().contribution()));
  }

  @Test
  @DisplayName("Transfer legs cancel for the tenant; a portfolio uses only its boundary leg")
  void securityTransfer() {
    var s = security(1, "Transfer", "CHF");
    var sell = booking(1, REDUCE, s, "CHF", 100);
    var buy = booking(2, ACCUMULATE, s, "CHF", -100);
    sell.setIdSecurityTransfer(42);
    buy.setIdSecurityTransfer(42);
    var before = holdings(1, "Transfer", 100);
    var after = holdings(1, "Transfer", 100);
    var tenant = BookingData.calculate(List.of(sell, buy), before, after, rates(false), false);
    assertEquals(0, tenant.attributedGain());
    assertTrue(tenant.positions().getFirst().transfer());
    assertEquals(0, BookingData.calculate(List.of(sell), before, null, rates(false), false).attributedGain());
    assertEquals(0, BookingData.calculate(List.of(buy), null, after, rates(false), false).attributedGain());
  }

  @Test
  @DisplayName("Absent rates poison only affected figures, including a missing fee; booked accrued interest stays in cash")
  void missingRatesAndAccruedInterest() {
    var fee = booking(1, FEE, null, "GBP", -10);
    var buy = booking(2, ACCUMULATE, security(1, "Bond", "CHF"), "CHF", -1010);
    var dividend = booking(3, DIVIDEND, security(2, "Unknown FX", "GBP"), "GBP", 10);
    var data = BookingData.calculate(List.of(fee, buy, dividend), null, holdings(1, "Bond", 1000), rates(false), false);
    assertEquals(2, data.missingRates());
    assertTrue(Double.isNaN(data.fees()));
    assertTrue(Double.isNaN(data.recordedCosts()));
    assertTrue(Double.isNaN(data.attributedGain()));
    assertEquals(-10, data.positions().getFirst().contribution()); // paid accrued interest excluded from valuation
    assertTrue(Double.isNaN(data.positions().get(1).income()));
    assertEquals(0, data.positions().get(1).transactionCosts()); // no booked cost, not a missing cost conversion
  }
}
