package grafioschtrader.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import grafioschtrader.config.NegativeIdNumberCreater;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Transaction;
import grafioschtrader.instrument.CostBasisMethods;
import grafioschtrader.instrument.SecurityCalcService;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.realizedgain.RealizedGainGrandTotal;
import grafioschtrader.reportviews.realizedgain.RealizedGainPosition;
import grafioschtrader.reportviews.realizedgain.RealizedGainYearGroup;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.CostBasisMethod;
import grafioschtrader.types.SpecialInvestmentInstruments;
import grafioschtrader.types.TransactionType;

/**
 * The realized gain report on the real transaction calculation with real domain objects. Every expected amount is
 * calculated by hand.
 */
@DisplayName("Realized gains per year")
class RealizedGainReportTest {

  private static final double EXACT = 1e-9;
  private static final int DEPOT_A = 11;
  private static final int DEPOT_B = 12;
  private static final int ID_USDCHF = 99;
  private static final LocalDate TODAY = LocalDate.now();
  private static final Map<String, Integer> PRECISION = Map.of("CHF", 2, "USD", 2, "EUR", 2);
  private static final Map<Integer, List<Securitysplit>> NO_SPLITS = Map.of();

  private SecurityCalcService service;
  private int nextId;
  private int nextSecurityId;

  @BeforeEach
  void setUp() {
    service = new SecurityCalcService();
    ReflectionTestUtils.setField(service, "negativeIdNumberCreater", new NegativeIdNumberCreater());
    nextId = 1;
    nextSecurityId = 1;
  }

  // Buy 100 at 10, buy 100 at 20, sell 100 at 30
  @ParameterizedTest(name = "{0}: the sale gains {1}, cost {2}, proceeds 3000")
  @CsvSource({ "AVERAGE_COST, 1500, 1500", "FIFO, 2000, 1000", "LIFO, 1000, 2000" })
  @DisplayName("The gain of a sale follows the method of the report and lands in the year of the sale")
  void saleGainByMethod(CostBasisMethod method, double gain, double costBase) {
    Security stock = stock("CHF");
    Cashaccount chf = cashaccount("CHF");

    RealizedGainGrandTotal total = run(CostBasisMethods.all(method), null, chfOnly(),
        Map.of(stock, List.of(buy(DEPOT_A, stock, chf, 100, 10, at(2024, 1)),
            buy(DEPOT_A, stock, chf, 100, 20, at(2024, 2)),
            sell(DEPOT_A, stock, chf, 100, 30, at(2024, 3)))));

    assertThat(total.years).extracting(year -> year.year).containsExactly(2024);
    RealizedGainPosition sale = total.years.getFirst().positions.getFirst();
    assertThat(sale.gainLoss).isEqualTo(gain, within(EXACT));
    assertThat(sale.costBase).isEqualTo(costBase, within(EXACT));
    assertThat(sale.proceeds).isEqualTo(3_000.0, within(EXACT));
    assertThat(sale.gainLossMC).isEqualTo(gain, within(EXACT));
    assertThat(sale.costBasisMethod).isEqualTo(method);
    assertThat(sale.units).isEqualTo(100.0);
    assertThat(total.appliedMethod).isEqualTo(method.name());
    assertThat(total.grandNetMC).isEqualTo(gain, within(EXACT));
  }

  @Test
  @DisplayName("Years keep their own sales, losses are positive amounts and a sale without gain is only counted")
  void gainsAndLossesPerYear() {
    Security stock = stock("CHF");
    Cashaccount chf = cashaccount("CHF");

    RealizedGainGrandTotal total = run(CostBasisMethods.all(CostBasisMethod.FIFO), null, chfOnly(),
        Map.of(stock, List.of(buy(DEPOT_A, stock, chf, 300, 10, at(2024, 1)),
            sell(DEPOT_A, stock, chf, 100, 15, at(2024, 2)),
            sell(DEPOT_A, stock, chf, 100, 10, at(2024, 3)), sell(DEPOT_A, stock, chf, 100, 7, at(2025, 4)))));

    assertThat(total.years).extracting(year -> year.year).containsExactly(2025, 2024);
    RealizedGainYearGroup y2024 = total.years.get(1);
    assertThat(y2024.yearGainMC).isEqualTo(500.0, within(EXACT));
    assertThat(y2024.yearLossMC).isZero();
    assertThat(y2024.saleCount).isEqualTo(2);
    RealizedGainYearGroup y2025 = total.years.getFirst();
    assertThat(y2025.yearGainMC).isZero();
    assertThat(y2025.yearLossMC).isEqualTo(300.0, within(EXACT));
    assertThat(y2025.yearNetMC).isEqualTo(-300.0, within(EXACT));
    assertThat(total.grandGainMC).isEqualTo(500.0, within(EXACT));
    assertThat(total.grandLossMC).isEqualTo(300.0, within(EXACT));
    assertThat(total.grandNetMC).isEqualTo(200.0, within(EXACT));
  }

  // USD security over a CHF account, booked rate of the sale 1.10: the gain of 200 USD is 220 CHF
  @Test
  @DisplayName("The main currency amount is the gain times the rate of the sale day, not of the report date")
  void foreignCurrencyAtSaleRate() {
    Security stock = stock("USD");
    Cashaccount chf = cashaccount("CHF");

    RealizedGainGrandTotal total = run(CostBasisMethods.all(CostBasisMethod.FIFO), null, usdChf(1.50),
        Map.of(stock, List.of(fx(buy(DEPOT_A, stock, chf, 1, 100, at(2024, 1)), 0.80),
            fx(sell(DEPOT_A, stock, chf, 1, 300, at(2024, 3)), 1.10))));

    RealizedGainPosition sale = total.years.getFirst().positions.getFirst();
    assertThat(sale.gainLoss).isEqualTo(200.0, within(EXACT));
    assertThat(sale.exchangeRate).isEqualTo(1.10);
    assertThat(sale.gainLossMC).isEqualTo(220.0, within(EXACT));
    assertThat(total.grandGainMC).isEqualTo(220.0, within(EXACT));
  }

  // The helper of the calculation returns null for a security in the main currency on an account in the main
  // currency, and the cross rate of the account for the same security on an account of another currency
  @ParameterizedTest(name = "security in the main currency over a cash account in {0}")
  @CsvSource({ "CHF", "USD" })
  @DisplayName("A security in the main currency has its gain as main currency amount, never times an account rate")
  void securityInMainCurrency(String accountCurrency) {
    Security stock = stock("CHF");
    Cashaccount account = cashaccount(accountCurrency);
    Transaction buy = buy(DEPOT_A, stock, account, 10, 100, at(2024, 1));
    Transaction sell = sell(DEPOT_A, stock, account, 10, 130, at(2024, 3));
    if (accountCurrency.equals("USD")) {
      // The cross rate of the account, which must not be applied to an amount in CHF
      buy.setIdCurrencypair(ID_USDCHF);
      buy.setCurrencyExRate(0.9);
      sell.setIdCurrencypair(ID_USDCHF);
      sell.setCurrencyExRate(0.9);
    }

    RealizedGainGrandTotal total = run(CostBasisMethods.all(CostBasisMethod.FIFO), null, usdChf(0.9),
        Map.of(stock, List.of(buy, sell)));

    RealizedGainPosition sale = total.years.getFirst().positions.getFirst();
    assertThat(sale.gainLoss).isEqualTo(300.0, within(EXACT));
    assertThat(sale.exchangeRate).isEqualTo(1.0);
    assertThat(sale.gainLossMC).isEqualTo(300.0, within(EXACT));
  }

  // USD security over a CHF account, main currency EUR. On the day of the sale USD/EUR 0.90 and CHF/EUR 1.05
  @Test
  @DisplayName("The rate of the security currency counts, the one of the cash account currency never replaces it")
  void securityCurrencyRateAgainstAccountCurrencyRate() {
    Security stock = stock("USD");
    Cashaccount chf = cashaccount("CHF");
    LocalDateTime buyTime = at(2024, 1);
    LocalDateTime saleTime = at(2024, 3);
    Transaction buy = buy(DEPOT_A, stock, chf, 1, 100, buyTime);
    Transaction sell = sell(DEPOT_A, stock, chf, 1, 300, saleTime);
    CostBasisMethods fifo = CostBasisMethods.all(CostBasisMethod.FIFO);

    RealizedGainGrandTotal withRate = run(fifo, null,
        eurMap(rate(buyTime, "USD", 0.80), rate(buyTime, "CHF", 1.00), rate(saleTime, "USD", 0.90),
            rate(saleTime, "CHF", 1.05)),
        Map.of(stock, List.of(buy, sell)));
    RealizedGainPosition sale = withRate.years.getFirst().positions.getFirst();
    assertThat(sale.exchangeRate).isEqualTo(0.90);
    assertThat(sale.gainLossMC).isEqualTo(180.0, within(EXACT));

    RealizedGainGrandTotal withoutRate = run(fifo, null,
        eurMap(rate(buyTime, "USD", 0.80), rate(buyTime, "CHF", 1.00), rate(saleTime, "CHF", 1.05)),
        Map.of(stock, List.of(buy, sell)));
    RealizedGainYearGroup year = withoutRate.years.getFirst();
    assertThat(year.positions.getFirst().gainLossMC).isNull();
    assertThat(year.incomplete).isTrue();
    assertThat(year.yearGainMC).isZero();
    assertThat(withoutRate.incomplete).isTrue();
  }

  // Security account A: buy 100 at 10, sell 100 at 30. Security account B: buy 100 at 20. Average cost is 15
  @Test
  @DisplayName("The security account filter selects sales and leaves their gain unchanged")
  void securityaccountFilterDoesNotChangeAverageGain() {
    Security stock = stock("CHF");
    Cashaccount chf = cashaccount("CHF");
    Map<Security, List<Transaction>> transactions = Map.of(stock,
        List.of(buy(DEPOT_A, stock, chf, 100, 10, at(2024, 1)),
        buy(DEPOT_B, stock, chf, 100, 20, at(2024, 2)), sell(DEPOT_A, stock, chf, 100, 30, at(2024, 3))));
    CostBasisMethods average = CostBasisMethods.all(CostBasisMethod.AVERAGE_COST);

    RealizedGainGrandTotal all = run(average, null, chfOnly(), transactions);
    RealizedGainGrandTotal onlyA = run(average, Set.of(DEPOT_A), chfOnly(), transactions);
    RealizedGainGrandTotal onlyB = run(average, Set.of(DEPOT_B), chfOnly(), transactions);

    assertThat(all.years.getFirst().positions.getFirst().gainLoss).isEqualTo(1_500.0, within(EXACT));
    assertThat(onlyA.years.getFirst().positions.getFirst().gainLoss).isEqualTo(1_500.0, within(EXACT));
    assertThat(onlyB.years).isEmpty();
  }

  @Test
  @DisplayName("Tenant methods differ per security account: the row names its method, the total says MIXED")
  void mixedMethods() {
    Security stock = stock("CHF");
    Cashaccount chf = cashaccount("CHF");
    CostBasisMethods mixed = new CostBasisMethods(CostBasisMethod.AVERAGE_COST, Map.of(DEPOT_B, CostBasisMethod.FIFO));

    RealizedGainGrandTotal total = run(mixed, null, chfOnly(),
        Map.of(stock, List.of(buy(DEPOT_A, stock, chf, 100, 10, at(2024, 1)),
            sell(DEPOT_A, stock, chf, 50, 20, at(2024, 2)),
            buy(DEPOT_B, stock, chf, 100, 10, at(2024, 3)), sell(DEPOT_B, stock, chf, 50, 20, at(2024, 4)))));

    assertThat(total.appliedMethod).isEqualTo(RealizedGainGrandTotal.MIXED_METHODS);
    assertThat(total.years.getFirst().positions).extracting(position -> position.costBasisMethod)
        .containsExactly(CostBasisMethod.AVERAGE_COST, CostBasisMethod.FIFO);
  }

  // Buy 100 at 10 in A, transfer to B on 31 December 2024 at the closing price 20, sell in B at 30 in 2025
  @Test
  @DisplayName("A transfer is a system booking with its own sums, together with the later sale it is the economic gain")
  void transferAsSystemBooking() {
    Security stock = stock("CHF");
    Cashaccount chf = cashaccount("CHF");
    LocalDateTime transferTime = LocalDateTime.of(2024, 12, 31, 12, 0);
    Transaction transferSell = sell(DEPOT_A, stock, chf, 100, 20, transferTime);
    transferSell.setIdSecurityTransfer(1);
    Transaction transferBuy = buy(DEPOT_B, stock, chf, 100, 20, transferTime);
    transferBuy.setIdSecurityTransfer(1);

    RealizedGainGrandTotal total = run(CostBasisMethods.all(CostBasisMethod.FIFO), null, chfOnly(),
        Map.of(stock, List.of(buy(DEPOT_A, stock, chf, 100, 10, at(2024, 1)), transferSell, transferBuy,
            sell(DEPOT_B, stock, chf, 100, 30, LocalDateTime.of(2025, 6, 1, 10, 0)))));

    RealizedGainYearGroup y2024 = total.years.get(1);
    assertThat(y2024.positions).singleElement().satisfies(position -> assertThat(position.systemBooking).isTrue());
    assertThat(y2024.yearNetMC).isZero();
    assertThat(y2024.saleCount).isZero();
    assertThat(y2024.systemBookingCount).isEqualTo(1);
    assertThat(y2024.systemGainMC).isEqualTo(1_000.0, within(EXACT));
    RealizedGainYearGroup y2025 = total.years.getFirst();
    assertThat(y2025.yearNetMC).isEqualTo(1_000.0, within(EXACT));
    assertThat(y2025.systemNetMC).isZero();
    // (30 - 10) * 100 over both years
    assertThat(total.grandNetMC + total.grandSystemNetMC).isEqualTo(2_000.0, within(EXACT));
  }

  // Buy 10 at 100 paying 20 accrued interest, sell 10 at 105 receiving 30
  @Test
  @DisplayName("The accrued interest rows of a bond sale are no sales and the gain excludes the interest")
  void bondSaleIsOneRow() {
    Security bond = security("CHF", AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.DIRECT_INVESTMENT);
    Cashaccount chf = cashaccount("CHF");
    Transaction buy = buy(DEPOT_A, bond, chf, 10, 100, at(2024, 1));
    buy.setAssetInvestmentValue1(20.0);
    Transaction sell = sell(DEPOT_A, bond, chf, 10, 105, at(2024, 3));
    sell.setAssetInvestmentValue1(30.0);

    RealizedGainGrandTotal total = run(CostBasisMethods.all(CostBasisMethod.FIFO), null, chfOnly(),
        Map.of(bond, List.of(buy, sell)));

    assertThat(total.years.getFirst().positions).hasSize(1);
    assertThat(total.years.getFirst().positions.getFirst().gainLoss).isEqualTo(50.0, within(EXACT));
  }

  // CFD: open 10 at 100, close 4 at 110
  @ParameterizedTest(name = "{0}")
  @CsvSource({ "AVERAGE_COST", "FIFO", "LIFO" })
  @DisplayName("The close of a margin position has the same gain with every method and no cost basis")
  void marginClose(CostBasisMethod method) {
    Security cfd = security("CHF", AssetclassType.EQUITIES, SpecialInvestmentInstruments.CFD);
    Cashaccount chf = cashaccount("CHF");
    Transaction open = buy(DEPOT_A, cfd, chf, 10, 100, at(2024, 1));
    open.setAssetInvestmentValue2(1.0);
    open.setCashaccountAmount(0.0);
    Transaction close = sell(DEPOT_A, cfd, chf, 4, 110, at(2024, 2));
    close.setAssetInvestmentValue2(1.0);
    close.setConnectedIdTransaction(open.getIdTransaction());
    close.setCashaccountAmount(40.0);

    RealizedGainGrandTotal total = run(CostBasisMethods.all(method), null, chfOnly(),
        Map.of(cfd, List.of(open, close)));

    RealizedGainPosition row = total.years.getFirst().positions.getFirst();
    assertThat(total.years.getFirst().positions).hasSize(1);
    assertThat(row.gainLoss).isEqualTo(40.0, within(EXACT));
    assertThat(row.costBase).isNull();
    assertThat(row.proceeds).isNull();
    assertThat(row.costBasisMethod).isNull();
  }

  @Test
  @DisplayName("A sale the purchases do not cover keeps the behaviour of the calculation and marks the year")
  void uncoveredSaleMarksTheYear() {
    Security stock = stock("CHF");
    Cashaccount chf = cashaccount("CHF");

    RealizedGainGrandTotal total = run(CostBasisMethods.all(CostBasisMethod.FIFO), null, chfOnly(),
        Map.of(stock, List.of(buy(DEPOT_A, stock, chf, 50, 10, at(2024, 1)),
            sell(DEPOT_A, stock, chf, 100, 30, at(2024, 2)))));

    RealizedGainPosition sale = total.years.getFirst().positions.getFirst();
    assertThat(sale.costBasisIncomplete).isTrue();
    // 50 covered units gain 1000, the 50 uncovered ones show no gain
    assertThat(sale.gainLoss).isEqualTo(1_000.0, within(EXACT));
    assertThat(total.years.getFirst().incomplete).isTrue();
    assertThat(total.incomplete).isTrue();
  }

  @Test
  @DisplayName("A tenant without sales gets an empty report")
  void noSales() {
    Security stock = stock("CHF");
    Cashaccount chf = cashaccount("CHF");

    RealizedGainGrandTotal empty = run(CostBasisMethods.all(CostBasisMethod.FIFO), null, chfOnly(), Map.of());
    RealizedGainGrandTotal onlyBuy = run(CostBasisMethods.all(CostBasisMethod.FIFO), null, chfOnly(),
        Map.of(stock, List.of(buy(DEPOT_A, stock, chf, 10, 10, at(2024, 1)))));

    assertThat(empty.years).isEmpty();
    assertThat(onlyBuy.years).isEmpty();
    assertThat(onlyBuy.grandNetMC).isZero();
  }

  // ---------------------------------------------------------------------------------------------------------------

  private RealizedGainGrandTotal run(CostBasisMethods methods, Set<Integer> idsSecurityaccount,
      DateTransactionCurrencypairMap currencyMap, Map<Security, List<Transaction>> transactions) {
    String mainCurrency = currencyMap.getMainCurrency();
    return new RealizedGainCalculator(service, mainCurrency, PRECISION, 2).calculate(new LinkedHashMap<>(transactions),
        NO_SPLITS, TODAY, currencyMap, methods, false, idsSecurityaccount);
  }

  private static DateTransactionCurrencypairMap chfOnly() {
    return new DateTransactionCurrencypairMap("CHF", TODAY, List.of(), List.of(), true, true, TODAY);
  }

  private static DateTransactionCurrencypairMap usdChf(double last) {
    Currencypair usdChf = new Currencypair("USD", "CHF");
    usdChf.setIdSecuritycurrency(ID_USDCHF);
    usdChf.setSLast(last);
    return new DateTransactionCurrencypairMap("CHF", TODAY, List.of(), List.of(usdChf), true, true, TODAY);
  }

  private static DateTransactionCurrencypairMap eurMap(Object[]... rates) {
    return new DateTransactionCurrencypairMap("EUR", TODAY, List.of(rates), List.of(), true, true, TODAY);
  }

  private static Object[] rate(LocalDateTime day, String currency, double rate) {
    return new Object[] { day.toLocalDate(), currency, rate };
  }

  private Security stock(String currency) {
    return security(currency, AssetclassType.EQUITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT);
  }

  private Security security(String currency, AssetclassType assetclassType, SpecialInvestmentInstruments instrument) {
    Assetclass assetclass = new Assetclass();
    assetclass.setCategoryType(assetclassType);
    assetclass.setSpecialInvestmentInstrument(instrument);
    Security security = new Security();
    security.setIdSecuritycurrency(nextSecurityId++);
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

  private static LocalDateTime at(int year, int month) {
    return LocalDateTime.of(year, month, 10, 10, 0);
  }

  private Transaction buy(int idSecurityaccount, Security security, Cashaccount cashaccount, double units,
      double quotation, LocalDateTime time) {
    return withId(nextId++, new Transaction(idSecurityaccount, cashaccount, security, units, quotation,
        TransactionType.ACCUMULATE, null, null, null, time, null));
  }

  private Transaction sell(int idSecurityaccount, Security security, Cashaccount cashaccount, double units,
      double quotation, LocalDateTime time) {
    return withId(nextId++, new Transaction(idSecurityaccount, cashaccount, security, units, quotation,
        TransactionType.REDUCE, null, null, null, time, null));
  }

  /** The date column is filled when a transaction is persisted, so the calculation reads it from a stored one. */
  private static Transaction withId(Integer idTransaction, Transaction transaction) {
    transaction.setIdTransaction(idTransaction);
    ReflectionTestUtils.setField(transaction, "transactionDate", transaction.getTransactionTime().toLocalDate());
    return transaction;
  }

  /** Marks a trade of a USD security settled over a CHF account with the booked exchange rate. */
  private static Transaction fx(Transaction transaction, double exchangeRate) {
    transaction.setIdCurrencypair(ID_USDCHF);
    transaction.setCurrencyExRate(exchangeRate);
    return transaction;
  }
}
