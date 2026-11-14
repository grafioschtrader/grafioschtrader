package grafioschtrader.instrument;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import grafioschtrader.config.NegativeIdNumberCreater;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Transaction;
import grafioschtrader.report.pdf.SecurityChartPdfComposer.Row;
import grafioschtrader.report.pdf.SecurityChartPdfRows;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.transaction.SecurityTransactionSummary;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;
import grafioschtrader.types.TransactionType;

/**
 * The rows of the chart PDF taken from the real calculation of the transaction list, with the amounts worked out by
 * hand: buy 100 at 10, buy 100 at 20, sell 100 at 30, then the hypothetical sale of the remaining 100 at 25. By the
 * average cost the sale takes 100 x 15 = 1500 of cost, so its gain is 3000 - 1500 = 1500; the 100 units left cost 1500
 * and bring 2500, a gain of 1000 or 1000 / 1500 = 66.67 %.
 */
@DisplayName("Rows of the chart PDF from the real transaction calculation")
class SecurityChartPdfRowsTest {

  private static final double EXACT = 1e-9;
  private static final int DEPOT = 11;
  private static final LocalDate TODAY = LocalDate.now();
  private static final Map<Integer, List<Securitysplit>> NO_SPLITS = Map.of();

  private SecurityCalcService service;
  private int nextId;

  @BeforeEach
  void setUp() {
    service = new SecurityCalcService();
    ReflectionTestUtils.setField(service, "negativeIdNumberCreater", new NegativeIdNumberCreater());
    nextId = 1;
  }

  @Test
  @DisplayName("The marked transactions keep their numbers and gains, the hypothetical sale closes the table")
  void numberedTransactionsGainAndHypotheticalSale() {
    Security stock = stock();
    Cashaccount chf = cashaccount();
    Transaction firstBuy = transaction(TransactionType.ACCUMULATE, stock, chf, 100, 10, 1);
    Transaction secondBuy = transaction(TransactionType.ACCUMULATE, stock, chf, 100, 20, 2);
    Transaction sale = transaction(TransactionType.REDUCE, stock, chf, 100, 30, 3);
    SecurityTransactionSummary summary = summaryWithOpenPosition(stock, firstBuy, secondBuy, sale);

    // The chart shows the first purchase and the sale; the second purchase is outside the displayed range
    List<Row> rows = SecurityChartPdfRows.of(summary.transactionPositionList,
        Map.of(sale.getIdTransaction(), 2, firstBuy.getIdTransaction(), 1));

    assertThat(rows).hasSize(3);
    Row purchase = rows.get(0);
    assertThat(purchase.number()).isEqualTo(1);
    assertThat(purchase.typeKey()).isEqualTo("ACCUMULATE");
    assertThat(purchase.units()).isEqualTo(100.0);
    assertThat(purchase.price()).isEqualTo(10.0);
    assertThat(purchase.gain()).as("a purchase has no gain").isNull();
    assertThat(purchase.hypothetical()).isFalse();

    Row saleRow = rows.get(1);
    assertThat(saleRow.number()).isEqualTo(2);
    assertThat(saleRow.typeKey()).isEqualTo("REDUCE");
    assertThat(saleRow.gain()).isEqualTo(1_500.0, within(EXACT));
    assertThat(saleRow.hypothetical()).isFalse();
    assertThat(saleRow.gainIncomplete()).isFalse();

    Row hypothetical = rows.get(2);
    assertThat(hypothetical.number()).as("the hypothetical sale has no marker").isNull();
    assertThat(hypothetical.hypothetical()).isTrue();
    assertThat(hypothetical.typeKey()).isEqualTo("HYPOTHETICAL_SELL");
    assertThat(hypothetical.units()).isEqualTo(100.0);
    assertThat(hypothetical.price()).isEqualTo(25.0);
    assertThat(hypothetical.amount()).isEqualTo(2_500.0, within(EXACT));
    assertThat(hypothetical.currency()).isEqualTo("CHF");
    assertThat(hypothetical.gain()).isEqualTo(1_000.0, within(EXACT));
    assertThat(hypothetical.gainPercentage()).isEqualTo(66.67, within(0.005));
    assertThat(hypothetical.estimateIncomplete()).isFalse();
  }

  @Test
  @DisplayName("A position still held shows its hypothetical sale even when the chart shows no marker")
  void hypotheticalSaleWithoutMarkers() {
    Security stock = stock();
    Cashaccount chf = cashaccount();
    SecurityTransactionSummary summary = summaryWithOpenPosition(stock,
        transaction(TransactionType.ACCUMULATE, stock, chf, 100, 10, 1),
        transaction(TransactionType.ACCUMULATE, stock, chf, 100, 20, 2),
        transaction(TransactionType.REDUCE, stock, chf, 100, 30, 3));

    List<Row> rows = SecurityChartPdfRows.of(summary.transactionPositionList, Map.of());

    assertThat(rows).extracting(Row::hypothetical).containsExactly(true);
  }

  @Test
  @DisplayName("A closed position has no hypothetical sale, and the database decides which transactions exist")
  void closedPositionAndForeignNumbers() {
    Security stock = stock();
    Cashaccount chf = cashaccount();
    Transaction buy = transaction(TransactionType.ACCUMULATE, stock, chf, 100, 10, 1);
    Transaction sale = transaction(TransactionType.REDUCE, stock, chf, 100, 12, 2);
    SecurityTransactionSummary summary = summaryWithOpenPosition(stock, buy, sale);

    // 999 names a transaction of someone else, which the list of this scope does not contain
    List<Row> rows = SecurityChartPdfRows.of(summary.transactionPositionList,
        Map.of(buy.getIdTransaction(), 1, sale.getIdTransaction(), 2, 999, 3));

    assertThat(rows).extracting(Row::number).containsExactly(1, 2);
    assertThat(rows).extracting(Row::hypothetical).containsOnly(false);
    assertThat(rows.get(1).gain()).isEqualTo(200.0, within(EXACT));
  }

  /** Calculates the list and values the units still held at 25, as the report does through the newest price. */
  private SecurityTransactionSummary summaryWithOpenPosition(Security stock, Transaction... transactions) {
    SecurityTransactionSummary summary = new SecurityTransactionSummary(stock, "CHF", Map.of("CHF", 2));
    DateTransactionCurrencypairMap currencies = new DateTransactionCurrencypairMap("CHF", TODAY, List.of(), List.of(),
        true, true, TODAY);
    service.calcTransactions(stock, false, summary, NO_SPLITS, Arrays.asList(transactions), TODAY, currencies,
        CostBasisMethods.all(grafioschtrader.types.CostBasisMethod.AVERAGE_COST));
    service.createHypotheticalSellTransaction(summary.securityPositionSummary, 25.0, NO_SPLITS, currencies, summary);
    return summary;
  }

  private Transaction transaction(TransactionType type, Security stock, Cashaccount cashaccount, double units,
      double quotation, int dayOfMonth) {
    Transaction transaction = new Transaction(DEPOT, cashaccount, stock, units, quotation, type, null, null, null,
        LocalDateTime.of(2024, 1, dayOfMonth, 10, 0), null);
    transaction.setIdTransaction(nextId++);
    return transaction;
  }

  private static Security stock() {
    Assetclass assetclass = new Assetclass();
    assetclass.setCategoryType(AssetclassType.EQUITIES);
    assetclass.setSpecialInvestmentInstrument(SpecialInvestmentInstruments.DIRECT_INVESTMENT);
    Security security = new Security();
    security.setIdSecuritycurrency(1);
    security.setName("Test");
    security.setCurrency("CHF");
    security.setAssetClass(assetclass);
    return security;
  }

  private static Cashaccount cashaccount() {
    Cashaccount cashaccount = new Cashaccount();
    cashaccount.setCurrency("CHF");
    return cashaccount;
  }
}
