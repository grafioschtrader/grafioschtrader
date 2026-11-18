package grafioschtrader.instrument;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import grafioschtrader.config.NegativeIdNumberCreater;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.reportviews.transaction.SecurityTransactionSummary;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.CostBasisMethod;
import grafioschtrader.types.SpecialInvestmentInstruments;
import grafioschtrader.types.TransactionType;

/**
 * {@link SecurityPositionSummary#positionFlow} is what the period performance per security reads as the flow of each
 * transaction. It is right when the identity {@code gainLossSecurity = units * price - positionFlow} holds after every
 * transaction, valued with a hypothetical sale without cost: then the flows and the values of a period add up to the
 * same gain the security account report shows. The cases cover every place that books a flow, every currency
 * constellation of the exposure checks the flow must not depend on, and the cost basis methods.
 */
@DisplayName("Flow of a position in the replay")
class SecurityPositionFlowTest {

  private static final double EXACT = 1e-9;
  private static final int DEPOT = 11;
  private static final LocalDate TODAY = LocalDate.of(2024, 12, 31);
  private static final Map<String, Integer> PRECISION = Map.of("CHF", 2, "USD", 2, "EUR", 2);
  private static final Map<Integer, List<Securitysplit>> NO_SPLITS = Map.of();
  private static final double VALUATION_PRICE = 130;

  private SecurityCalcService service;
  private int nextId;

  @BeforeEach
  void setUp() {
    service = new SecurityCalcService();
    ReflectionTestUtils.setField(service, "negativeIdNumberCreater", new NegativeIdNumberCreater());
    nextId = 1;
  }

  @ParameterizedTest(name = "excludeDivTax = {0}")
  @ValueSource(booleans = { false, true })
  @DisplayName("Share in the main currency: purchases, a dividend and a sale, after every transaction")
  void shareInMainCurrency(boolean excludeDivTax) {
    Security stock = security("CHF", AssetclassType.EQUITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT);
    Cashaccount chf = cashaccount("CHF");
    List<Transaction> transactions = List.of(trade(stock, chf, TransactionType.ACCUMULATE, 10, 100, 5.0, 1),
        trade(stock, chf, TransactionType.ACCUMULATE, 5, 110, 3.0, 2), dividend(stock, chf, 15, 2, 3.0, 3),
        trade(stock, chf, TransactionType.REDUCE, 8, 120, 4.0, 4));
    assertIdentityAfterEveryTransaction(stock, transactions, excludeDivTax, chfOnly());
  }

  @Test
  @DisplayName("Share in USD over a EUR account, main currency CHF: the flow does not depend on the rates")
  void foreignShareOverThirdCurrencyAccount() {
    Security stock = security("USD", AssetclassType.EQUITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT);
    Cashaccount eur = cashaccount("EUR");
    List<Transaction> transactions = List.of(trade(stock, eur, TransactionType.ACCUMULATE, 10, 100, 5.0, 1),
        dividend(stock, eur, 10, 2, 0.6, 2), trade(stock, eur, TransactionType.REDUCE, 4, 120, 4.0, 3));
    List<Object[]> eurRates = new ArrayList<>();
    for (int day = 1; day <= 3; day++) {
      eurRates.add(new Object[] { day(day).toLocalDate(), "EUR", 0.95 });
    }
    Currencypair usdChf = new Currencypair("USD", "CHF");
    usdChf.setIdSecuritycurrency(99);
    usdChf.setSLast(0.9);
    DateTransactionCurrencypairMap currencyMap = new DateTransactionCurrencypairMap("CHF", TODAY, eurRates,
        List.of(usdChf), true, true, TODAY);
    assertIdentityAfterEveryTransaction(stock, transactions, false, currencyMap);
  }

  @Test
  @DisplayName("Share in the main currency over a USD account: no currency exposure, the flow is still booked")
  void mainCurrencyShareOverForeignAccount() {
    Security stock = security("CHF", AssetclassType.EQUITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT);
    Cashaccount usd = cashaccount("USD");
    List<Transaction> transactions = List.of(trade(stock, usd, TransactionType.ACCUMULATE, 10, 100, 5.0, 1),
        trade(stock, usd, TransactionType.REDUCE, 4, 120, 4.0, 2));
    assertIdentityAfterEveryTransaction(stock, transactions, false, chfOnly());
  }

  @Test
  @DisplayName("Bond with accrued interest on purchase and sale: closed position, gain = - positionFlow")
  void bondWithAccruedInterest() {
    Security bond = security("CHF", AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.DIRECT_INVESTMENT);
    Cashaccount chf = cashaccount("CHF");
    Transaction buy = trade(bond, chf, TransactionType.ACCUMULATE, 1000, 99, 10.0, 1);
    buy.setAssetInvestmentValue1(12.0);
    Transaction sell = trade(bond, chf, TransactionType.REDUCE, 1000, 101, 10.0, 2);
    sell.setAssetInvestmentValue1(20.0);
    SecurityPositionSummary position = run(bond, false, chfOnly(), CostBasisMethods.all(CostBasisMethod.AVERAGE_COST),
        buy, sell).securityPositionSummary;
    assertThat(position.units).isEqualTo(0.0, within(EXACT));
    // Paid 99000 + 10 cost + 12 accrued interest, received 101000 - 10 cost + 20 accrued interest
    assertThat(position.positionFlow).isEqualTo(99_022.0 - 101_010.0, within(EXACT));
    assertThat(position.gainLossSecurity).isEqualTo(-position.positionFlow, within(EXACT));
  }

  @Test
  @DisplayName("Margin position with finance cost: every settled amount enters the flow")
  void marginWithFinanceCost() {
    Security cfd = security("CHF", AssetclassType.EQUITIES, SpecialInvestmentInstruments.CFD);
    Cashaccount chf = cashaccount("CHF");
    Transaction open = margin(cfd, chf, TransactionType.ACCUMULATE, 10, 100, -5.0, null, 1);
    Transaction financeCost = margin(cfd, chf, TransactionType.FINANCE_COST, 0, 0, -2.0, open.getIdTransaction(), 2);
    Transaction close = margin(cfd, chf, TransactionType.REDUCE, 10, 110, 95.0, open.getIdTransaction(), 3);
    SecurityPositionSummary position = run(cfd, false, chfOnly(), CostBasisMethods.all(CostBasisMethod.AVERAGE_COST),
        open, financeCost, close).securityPositionSummary;
    assertThat(position.positionFlow).isEqualTo(-position.gainLossSecurity, within(EXACT));
    assertThat(position.positionFlow).isNotEqualTo(0.0);
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(CostBasisMethod.class)
  @DisplayName("A completely sold position has the same flow and gain under every cost basis method")
  void independentOfCostBasisMethod(CostBasisMethod method) {
    Security stock = security("CHF", AssetclassType.EQUITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT);
    Cashaccount chf = cashaccount("CHF");
    SecurityPositionSummary position = run(stock, false, chfOnly(), CostBasisMethods.all(method),
        trade(stock, chf, TransactionType.ACCUMULATE, 100, 10, 1.0, 1),
        trade(stock, chf, TransactionType.ACCUMULATE, 100, 20, 1.0, 2),
        trade(stock, chf, TransactionType.REDUCE, 120, 30, 1.0, 3),
        trade(stock, chf, TransactionType.REDUCE, 80, 25, 1.0, 4)).securityPositionSummary;
    // Paid 1000 + 2000 + 2 cost, received 3600 + 2000 - 2 cost
    assertThat(position.positionFlow).isEqualTo(3_002.0 - 5_598.0, within(EXACT));
    assertThat(position.gainLossSecurity).isEqualTo(2_596.0, within(EXACT));
  }

  /** Runs every prefix of the transactions, values it with a hypothetical sale and checks the identity. */
  private void assertIdentityAfterEveryTransaction(Security security, List<Transaction> transactions,
      boolean excludeDivTax, DateTransactionCurrencypairMap currencyMap) {
    for (int count = 1; count <= transactions.size(); count++) {
      SecurityTransactionSummary summary = run(security, excludeDivTax, currencyMap,
          CostBasisMethods.all(CostBasisMethod.AVERAGE_COST),
          transactions.subList(0, count).toArray(Transaction[]::new));
      SecurityPositionSummary position = summary.securityPositionSummary;
      double value = position.units * VALUATION_PRICE;
      double flowBeforeSale = position.positionFlow;
      service.createHypotheticalSellTransaction(position, VALUATION_PRICE, NO_SPLITS, currencyMap, summary);
      assertThat(position.positionFlow).as("the hypothetical sale adds no flow").isEqualTo(flowBeforeSale);
      assertThat(position.gainLossSecurity).as("after %d transactions", count).isEqualTo(value - flowBeforeSale,
          within(EXACT));
    }
  }

  private SecurityTransactionSummary run(Security security, boolean excludeDivTax,
      DateTransactionCurrencypairMap currencyMap, CostBasisMethods methods, Transaction... transactions) {
    SecurityTransactionSummary summary = new SecurityTransactionSummary(security, "CHF", PRECISION);
    service.calcTransactions(security, excludeDivTax, summary, NO_SPLITS, Arrays.asList(transactions), TODAY,
        currencyMap, methods);
    return summary;
  }

  private static DateTransactionCurrencypairMap chfOnly() {
    return new DateTransactionCurrencypairMap("CHF", TODAY, List.of(), List.of(), true, true, TODAY);
  }

  private static Security security(String currency, AssetclassType categoryType,
      SpecialInvestmentInstruments instrument) {
    Assetclass assetclass = new Assetclass();
    assetclass.setCategoryType(categoryType);
    assetclass.setSpecialInvestmentInstrument(instrument);
    Security security = new Security();
    security.setIdSecuritycurrency(1);
    security.setName("Test");
    security.setCurrency(currency);
    security.setAssetClass(assetclass);
    return security;
  }

  private static Cashaccount cashaccount(String currency) {
    Cashaccount cashaccount = new Cashaccount();
    cashaccount.setCurrency(currency);
    return cashaccount;
  }

  private static LocalDateTime day(int dayOfMonth) {
    return LocalDateTime.of(2024, 1, dayOfMonth, 10, 0);
  }

  private Transaction trade(Security security, Cashaccount cashaccount, TransactionType type, double units,
      double quotation, Double transactionCost, int dayOfMonth) {
    Transaction transaction = new Transaction(DEPOT, cashaccount, security, units, quotation, type, null,
        transactionCost, null, day(dayOfMonth), null);
    transaction.setIdTransaction(nextId++);
    // Fills the business date as persisting would
    transaction.onPrePersist();
    return transaction;
  }

  private Transaction dividend(Security security, Cashaccount cashaccount, double units, double perUnit,
      Double taxCost, int dayOfMonth) {
    Transaction transaction = new Transaction(DEPOT, cashaccount, security, units, perUnit, TransactionType.DIVIDEND,
        taxCost, null, null, day(dayOfMonth), null);
    transaction.setIdTransaction(nextId++);
    // Fills the business date as persisting would
    transaction.onPrePersist();
    return transaction;
  }

  private Transaction margin(Security security, Cashaccount cashaccount, TransactionType type, double units,
      double quotation, double cashaccountAmount, Integer connectedIdTransaction, int dayOfMonth) {
    Transaction transaction = new Transaction(DEPOT, cashaccount, security, units, quotation, type, null, null, null,
        day(dayOfMonth), null);
    transaction.setIdTransaction(nextId++);
    // Fills the business date as persisting would
    transaction.onPrePersist();
    transaction.setCashaccountAmount(cashaccountAmount);
    transaction.setAssetInvestmentValue2(1.0);
    transaction.setConnectedIdTransaction(connectedIdTransaction);
    return transaction;
  }
}
