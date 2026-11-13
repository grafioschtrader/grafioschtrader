package grafioschtrader.instrument;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import grafioschtrader.config.NegativeIdNumberCreater;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.securityaccount.CostLot;
import grafioschtrader.reportviews.securityaccount.SecurityPositionCurrenyGroupSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionGrandSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.reportviews.transaction.SecurityTransactionPosition;
import grafioschtrader.reportviews.transaction.SecurityTransactionSummary;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.CostBasisMethod;
import grafioschtrader.types.CreateType;
import grafioschtrader.types.SpecialInvestmentInstruments;
import grafioschtrader.types.TransactionType;

/**
 * The cost basis methods on the real calculation with real domain objects. Every expected amount is calculated by
 * hand. The total gain is the same for every method by construction, so it cannot show a wrongly consumed lot; the
 * tests therefore check the cost of each sale, the lots it consumed and the cost that remains.
 */
@DisplayName("Cost basis methods of the security calculation")
class SecurityGeneralCalcCostBasisTest {

  private static final double EXACT = 1e-9;
  private static final int DEPOT_A = 11;
  private static final int DEPOT_B = 12;
  private static final int ID_USDCHF = 99;
  private static final LocalDate TODAY = LocalDate.now();
  private static final Map<String, Integer> PRECISION = Map.of("CHF", 2, "USD", 2);
  private static final Map<Integer, List<Securitysplit>> NO_SPLITS = Map.of();

  private SecurityCalcService service;
  private int nextId;

  @BeforeEach
  void setUp() {
    service = new SecurityCalcService();
    ReflectionTestUtils.setField(service, "negativeIdNumberCreater", new NegativeIdNumberCreater());
    nextId = 1;
  }

  // Buy 100 at 10, buy 100 at 20, sell 100 at 30, price 25
  @ParameterizedTest(name = "{0}: sale costs {1}, {3} remain, open return {4} %")
  @CsvSource({ "AVERAGE_COST, 1500, 1500, 1500, 66.67", "FIFO, 1000, 2000, 2000, 25.00",
      "LIFO, 2000, 1000, 1000, 150.00" })
  @DisplayName("A partial sale takes the cost its method assigns, the total gain stays the same")
  void partialSale(CostBasisMethod method, double saleCost, double realized, double remainingCost,
      double openReturn) {
    Security stock = stock("CHF");
    Cashaccount chf = cashaccount("CHF");
    Transaction[] transactions = { buy(DEPOT_A, stock, chf, 100, 10, day(1)), buy(DEPOT_A, stock, chf, 100, 20, day(2)),
        sell(DEPOT_A, stock, chf, 100, 30, day(3)) };

    // Valued as the holdings report, the alert and the treemap do
    SecurityTransactionSummary valued = run(stock, CostBasisMethods.all(method), transactions);
    SecurityTransactionPosition sale = valued.transactionPositionList.getLast();
    assertThat(sale.transactionGainLoss).isEqualTo(realized, within(EXACT));
    assertThat(sale.costBasisMethod).isEqualTo(method);
    SecurityPositionSummary position = valued.securityPositionSummary;
    assertThat(position.adjustedCostBase).isEqualTo(remainingCost, within(EXACT));
    assertThat(position.transactionCostBase).isEqualTo(saleCost, within(EXACT));
    position.calcGainLossByPrice(25.0);
    assertThat(position.transactionGainLossPercentage).isEqualTo(openReturn);
    assertThat(position.getPositionGainLossPercentage()).isEqualTo(openReturn);
    // realized + unrealized = proceeds + market value - purchase costs = 3000 + 2500 - 3000
    assertThat(position.gainLossSecurity).isEqualTo(2_500.0, within(EXACT));

    // Valued as the watchlist does, through the hypothetical sale of the whole position
    SecurityTransactionSummary hypothetical = run(stock, CostBasisMethods.all(method), transactions);
    service.createHypotheticalSellTransaction(hypothetical.securityPositionSummary, 25.0, NO_SPLITS, chfOnly(TODAY),
        hypothetical);
    assertThat(hypothetical.securityPositionSummary.getPositionGainLossPercentage()).isEqualTo(openReturn);
    assertThat(hypothetical.securityPositionSummary.getPositionGainLoss()).isEqualTo(2_500.0 - realized,
        within(EXACT));
    // The transaction of the hypothetical sale has no security account, so no method is resolved for it
    assertThat(hypothetical.transactionPositionList.getLast().costBasisMethod).isNull();
    assertThat(hypothetical.transactionPositionList.getLast().consumedLots).isNull();
  }

  // USD security, main currency CHF: buy 1 at 100 (rate 0.80), buy 1 at 200 (1.00), sell 1 at 300 (1.10),
  // remaining unit valued at 250 (1.20)
  @ParameterizedTest(name = "{0}: sale {1} CHF, open position {2} CHF or {3} %")
  @CsvSource({ "AVERAGE_COST, 165, 170, 94.44", "FIFO, 220, 110, 45.83", "LIFO, 110, 230, 191.67" })
  @DisplayName("The main currency figures follow the cost of the method, the currency result does not")
  void mainCurrency(CostBasisMethod method, double saleGainMC, double positionGainLossMC, double percentageMC) {
    Security stock = stock("USD");
    Cashaccount chf = cashaccount("CHF");
    DateTransactionCurrencypairMap dateCurrencyMap = usdChf(TODAY, 1.20);
    SecurityTransactionSummary summary = new SecurityTransactionSummary(stock, "CHF", PRECISION);
    service.calcTransactions(stock, false, summary, NO_SPLITS,
        List.of(fx(buy(DEPOT_A, stock, chf, 1, 100, day(1)), 0.80), fx(buy(DEPOT_A, stock, chf, 1, 200, day(2)), 1.00),
            fx(sell(DEPOT_A, stock, chf, 1, 300, day(3)), 1.10)),
        TODAY, dateCurrencyMap, CostBasisMethods.all(method));
    assertThat(summary.transactionPositionList.getLast().getTransactionGainLossMC()).isEqualTo(saleGainMC);

    SecurityPositionSummary position = summary.securityPositionSummary;
    service.createHypotheticalSellTransaction(position, 250.0, NO_SPLITS, dateCurrencyMap, null);
    position.calcMainCurrency(1.20);

    // Invested 100 + 200 - 300 = 0 USD, at the rates of the flows 80 + 200 - 330 = -50 CHF
    assertThat(position.getGainLossCurrencyMC()).isEqualTo(50.0);
    assertThat(position.getPositionGainLossMC()).isEqualTo(positionGainLossMC);
    assertThat(position.getPositionGainLossPercentageMC()).isEqualTo(percentageMC);
  }

  @Test
  @DisplayName("Lots stand on the split basis of the report date, their cost is unchanged by a split")
  void splitAndReverseSplit() {
    Security stock = stock("CHF");
    Cashaccount chf = cashaccount("CHF");
    CostBasisMethods fifo = CostBasisMethods.all(CostBasisMethod.FIFO);
    LocalDate splitDate = LocalDate.of(2024, 6, 1);
    Transaction[] transactions = {
        buy(DEPOT_A, stock, chf, 100, 10, LocalDateTime.of(2024, 1, 10, 10, 0)),
        buy(DEPOT_A, stock, chf, 100, 8, LocalDateTime.of(2024, 7, 1, 10, 0)),
        sell(DEPOT_A, stock, chf, 150, 9, LocalDateTime.of(2024, 8, 1, 10, 0)) };

    // Split 1:2. After it the first purchase counts 200 units for its 1000, the sale of 150 takes three quarters of it
    Map<Integer, List<Securitysplit>> split = splits(stock, splitDate, 1, 2);
    SecurityTransactionSummary after = run(stock, fifo, LocalDate.of(2025, 1, 1), split, transactions);
    SecurityTransactionPosition sale = after.transactionPositionList.getLast();
    assertThat(sale.consumedLots).extracting(lot -> lot.units).containsExactly(150.0);
    assertThat(sale.consumedLots.getFirst().cost).isEqualTo(750.0, within(EXACT));
    assertThat(sale.transactionGainLoss).isEqualTo(600.0, within(EXACT));
    List<CostLot> open = after.securityPositionSummary.getCostLedger(DEPOT_A).getLots();
    assertThat(open).extracting(lot -> lot.units).containsExactly(50.0, 100.0);
    assertThat(open).extracting(lot -> lot.cost).containsExactly(250.0, 800.0);

    // A report date before the split sees the first purchase with the units as bought
    SecurityTransactionSummary before = run(stock, fifo, LocalDate.of(2024, 3, 1), split, transactions);
    List<CostLot> openBefore = before.securityPositionSummary.getCostLedger(DEPOT_A).getLots();
    assertThat(openBefore).extracting(lot -> lot.units).containsExactly(100.0);
    assertThat(openBefore).extracting(lot -> lot.cost).containsExactly(1_000.0);

    // Reverse split 10:1. The 1000 units bought at 1 become 100 units, a sale of 40 takes 40 % of the cost
    SecurityTransactionSummary reverse = run(stock, fifo, LocalDate.of(2025, 1, 1),
        splits(stock, splitDate, 10, 1), buy(DEPOT_A, stock, chf, 1_000, 1, LocalDateTime.of(2024, 1, 10, 10, 0)),
        sell(DEPOT_A, stock, chf, 40, 12, LocalDateTime.of(2024, 8, 1, 10, 0)));
    assertThat(reverse.securityPositionSummary.transactionCostBase).isEqualTo(400.0, within(EXACT));
    assertThat(reverse.securityPositionSummary.getCostLedger(DEPOT_A).getLots()).extracting(lot -> lot.units)
        .containsExactly(60.0);
  }

  // Buy 10 at 100 paying 20 accrued interest, buy 10 at 110 paying 10, sell 10 at 105 receiving 30, price 108
  @ParameterizedTest(name = "{0}: sale costs {1}")
  @CsvSource({ "AVERAGE_COST, 1050", "FIFO, 1000", "LIFO, 1100" })
  @DisplayName("Accrued interest of a bond is income and never part of a lot")
  void bondWithAccruedInterest(CostBasisMethod method, double saleCost) {
    Security bond = security("CHF", AssetclassType.FIXED_INCOME);
    Cashaccount chf = cashaccount("CHF");
    Transaction first = buy(DEPOT_A, bond, chf, 10, 100, day(1));
    first.setAssetInvestmentValue1(20.0);
    Transaction second = buy(DEPOT_A, bond, chf, 10, 110, day(2));
    second.setAssetInvestmentValue1(10.0);
    Transaction sale = sell(DEPOT_A, bond, chf, 10, 105, day(3));
    sale.setAssetInvestmentValue1(30.0);

    SecurityPositionSummary position = run(bond, CostBasisMethods.all(method), first, second,
        sale).securityPositionSummary;

    assertThat(position.transactionCostBase).isEqualTo(saleCost, within(EXACT));
    assertThat(position.adjustedCostBase).isEqualTo(2_100.0 - saleCost, within(EXACT));
    position.calcGainLossByPrice(108.0);
    // Proceeds 1050 + market value 1080 - purchases 2100, plus interest -20 - 10 + 30
    assertThat(position.gainLossSecurity).isEqualTo(30.0, within(EXACT));
  }

  @Test
  @DisplayName("FIFO in one portfolio takes its purchases out of the average pool of a view over both")
  void twoSecurityAccountsWithDifferentMethods() {
    Security stock = stock("CHF");
    Cashaccount chf = cashaccount("CHF");
    Transaction buyA = buy(DEPOT_A, stock, chf, 100, 10, day(1));
    Transaction buyB = buy(DEPOT_B, stock, chf, 100, 20, day(2));
    Transaction sellA = sell(DEPOT_A, stock, chf, 100, 30, day(3));

    // Both on average cost: the view over both security accounts pools 100 at 10 with 100 at 20
    assertThat(lastGain(run(stock, CostBasisMethods.AVERAGE_ONLY, buyA, buyB, sellA))).isEqualTo(1_500.0,
        within(EXACT));
    // B on FIFO: its purchase leaves the pool, the sale of A meets only the purchase of A
    CostBasisMethods onlyBFifo = new CostBasisMethods(CostBasisMethod.AVERAGE_COST,
        Map.of(DEPOT_B, CostBasisMethod.FIFO));
    SecurityTransactionSummary mixed = run(stock, onlyBFifo, buyA, buyB, sellA);
    assertThat(lastGain(mixed)).isEqualTo(2_000.0, within(EXACT));
    assertThat(mixed.transactionPositionList.getLast().costBasisMethod).isEqualTo(CostBasisMethod.AVERAGE_COST);
    assertThat(mixed.securityPositionSummary.adjustedCostBase).isEqualTo(2_000.0, within(EXACT));
    // The view of security account A alone shows 2000 in both cases
    assertThat(lastGain(run(stock, CostBasisMethods.AVERAGE_ONLY, buyA, sellA))).isEqualTo(2_000.0, within(EXACT));
  }

  @Test
  @DisplayName("With FIFO the order in which a report walks the security accounts does not matter")
  void securityAccountOrderIsIrrelevantForFifo() {
    Security stock = stock("CHF");
    Cashaccount chf = cashaccount("CHF");
    List<Transaction> accountA = List.of(buy(DEPOT_A, stock, chf, 100, 10, day(1)),
        sell(DEPOT_A, stock, chf, 50, 30, day(4)));
    List<Transaction> accountB = List.of(buy(DEPOT_B, stock, chf, 100, 20, day(2)),
        sell(DEPOT_B, stock, chf, 50, 30, day(3)));
    CostBasisMethods fifo = CostBasisMethods.all(CostBasisMethod.FIFO);

    // The holdings report walks one security account after the other and not chronologically
    for (List<List<Transaction>> order : List.of(List.of(accountA, accountB), List.of(accountB, accountA))) {
      SecurityGeneralCalc calc = new SecurityGeneralCalc();
      SecurityTransactionSummary summary = new SecurityTransactionSummary(stock, "CHF", PRECISION);
      order.forEach(account -> account.forEach(transaction -> calc.calcTransactionAndAddToPosition(transaction,
          summary, false, NO_SPLITS, chfOnly(TODAY), new NegativeIdNumberCreater(), fifo)));

      assertThat(gainOf(summary, accountA.getLast())).isEqualTo(1_000.0, within(EXACT));
      assertThat(gainOf(summary, accountB.getLast())).isEqualTo(500.0, within(EXACT));
    }
  }

  @Test
  @DisplayName("Transactions of the same minute are ordered purchase, income, sale, transfer purchase, then id")
  void orderOfSimultaneousTransactions() {
    Security stock = stock("CHF");
    Cashaccount chf = cashaccount("CHF");
    LocalDateTime time = day(5);
    Transaction dividend = withId(5, new Transaction(DEPOT_A, chf, stock, 100.0, 1.0, TransactionType.DIVIDEND, null,
        null, null, time, null));
    Transaction sale = withId(2, sell(DEPOT_A, stock, chf, 10, 30, time));
    Transaction purchase = withId(9, buy(DEPOT_A, stock, chf, 10, 30, time));
    Transaction unsavedPurchase = withId(null, buy(DEPOT_A, stock, chf, 10, 30, time));
    Transaction transferPurchase = withId(1, buy(DEPOT_B, stock, chf, 10, 30, time));
    transferPurchase.setIdSecurityTransfer(7);
    Transaction earlier = withId(50, sell(DEPOT_A, stock, chf, 10, 30, time.minusMinutes(1)));

    List<Transaction> shuffled = new ArrayList<>(
        List.of(dividend, sale, purchase, unsavedPurchase, transferPurchase, earlier));
    Random random = new Random(42);
    for (int i = 0; i < 50; i++) {
      Collections.shuffle(shuffled, random);
      assertThat(SecurityCalcService.sortForCalculation(shuffled)).containsExactly(earlier, purchase, unsavedPurchase,
          dividend, sale, transferPurchase);
    }
  }

  @Test
  @DisplayName("A margin position opened by a sale stays ahead of the purchase that closes it in the same minute")
  void marginOpenAndCloseKeepTheirOrder() {
    Security cfd = security("CHF", AssetclassType.EQUITIES);
    cfd.getAssetClass().setSpecialInvestmentInstrument(SpecialInvestmentInstruments.CFD);
    Cashaccount chf = cashaccount("CHF");
    Transaction open = withId(1, sell(DEPOT_A, cfd, chf, 10, 30, day(5)));
    Transaction close = withId(2, buy(DEPOT_A, cfd, chf, 10, 28, day(5)));
    close.setConnectedIdTransaction(1);

    assertThat(SecurityCalcService.sortForCalculation(List.of(close, open))).containsExactly(open, close);
    assertThat(SecurityCalcService.sortForCalculation(List.of(open, close))).containsExactly(open, close);
  }

  @Test
  @DisplayName("After closing out, a new purchase starts without a lot or a cost of the closed position")
  void closeOutAndRebuy() {
    Security stock = stock("CHF");
    Cashaccount chf = cashaccount("CHF");
    Transaction rebuy = buy(DEPOT_A, stock, chf, 50, 20, day(3));

    SecurityTransactionSummary summary = run(stock, CostBasisMethods.all(CostBasisMethod.FIFO),
        buy(DEPOT_A, stock, chf, 100, 10, day(1)), sell(DEPOT_A, stock, chf, 100, 12, day(2)), rebuy,
        sell(DEPOT_A, stock, chf, 50, 25, day(4)));

    SecurityTransactionPosition sale = summary.transactionPositionList.getLast();
    assertThat(sale.consumedLots).extracting(lot -> lot.idTransaction).containsExactly(rebuy.getIdTransaction());
    assertThat(sale.consumedLots.getFirst().cost).isEqualTo(1_000.0, within(EXACT));
    assertThat(sale.transactionGainLoss).isEqualTo(250.0, within(EXACT));
    assertThat(summary.securityPositionSummary.getCostLedger(DEPOT_A).isEmpty()).isTrue();
    assertThat(summary.securityPositionSummary.adjustedCostBase).isEqualTo(0.0, within(EXACT));
  }

  @ParameterizedTest
  @EnumSource(CostBasisMethod.class)
  @DisplayName("Without a cost every percentage is empty instead of infinite")
  void freeLot(CostBasisMethod method) {
    Security stock = stock("CHF");
    Cashaccount chf = cashaccount("CHF");
    Transaction[] transactions = { buy(DEPOT_A, stock, chf, 10, 0, day(1)),
        new Transaction(DEPOT_A, chf, stock, 10.0, 1.0, TransactionType.DIVIDEND, null, null, null, day(2), null),
        sell(DEPOT_A, stock, chf, 5, 10, day(3)) };

    SecurityTransactionSummary valued = run(stock, CostBasisMethods.all(method), transactions);
    assertThat(valued.transactionPositionList).extracting(row -> row.transactionGainLossPercentage)
        .containsOnlyNulls();
    assertThat(valued.transactionPositionList.getLast().transactionGainLoss).isEqualTo(50.0, within(EXACT));
    SecurityPositionSummary position = valued.securityPositionSummary;
    position.calcGainLossByPrice(10.0);
    assertThat(position.transactionGainLossPercentage).isNull();
    assertThat(position.getPositionGainLossPercentage()).isNull();
    // Dividend 10 + sale 50 + remaining 5 units at 10
    assertThat(position.gainLossSecurity).isEqualTo(110.0, within(EXACT));

    SecurityTransactionSummary hypothetical = run(stock, CostBasisMethods.all(method), transactions);
    service.createHypotheticalSellTransaction(hypothetical.securityPositionSummary, 10.0, NO_SPLITS, chfOnly(TODAY),
        null);
    hypothetical.securityPositionSummary.calcMainCurrency(1.0);
    assertThat(hypothetical.securityPositionSummary.getPositionGainLossPercentage()).isNull();
    assertThat(hypothetical.securityPositionSummary.getPositionGainLossPercentageMC()).isNull();
    assertThat(hypothetical.securityPositionSummary.getPositionGainLoss()).isEqualTo(50.0, within(EXACT));
  }

  @Test
  @DisplayName("A sale the lots do not cover shows no gain for the missing units and leaves the average pool alone")
  void uncoveredSale() {
    Security stock = stock("CHF");
    Cashaccount chf = cashaccount("CHF");
    CostBasisMethods onlyAFifo = new CostBasisMethods(CostBasisMethod.AVERAGE_COST,
        Map.of(DEPOT_A, CostBasisMethod.FIFO));

    SecurityTransactionSummary summary = run(stock, onlyAFifo, buy(DEPOT_B, stock, chf, 100, 20, day(1)),
        buy(DEPOT_A, stock, chf, 4, 10, day(2)), sell(DEPOT_A, stock, chf, 10, 30, day(3)));

    SecurityTransactionPosition sale = summary.transactionPositionList.getLast();
    // 4 units cost 40, the 6 missing units take 6/10 of the proceeds of 300 as their cost
    assertThat(sale.transactionGainLoss).isEqualTo(80.0, within(EXACT));
    assertThat(sale.costBasisIncomplete).isTrue();
    assertThat(sale.transactionGainLossPercentage).isNull();
    assertThat(summary.securityPositionSummary.averageCost()).isEqualTo(2_000.0, within(EXACT));
    assertThat(summary.securityPositionSummary.averageUnits()).isEqualTo(100.0, within(EXACT));
  }

  @Test
  @DisplayName("A transfer between security accounts realizes the sale before its purchase enters the pool again")
  void transferOnAverageCost() {
    Security stock = stock("CHF");
    Cashaccount chf = cashaccount("CHF");
    LocalDateTime noon = LocalDate.of(2024, 3, 1).atTime(12, 0);
    Transaction buyA = buy(DEPOT_A, stock, chf, 100, 10, day(1));
    Transaction transferSale = sell(DEPOT_A, stock, chf, 100, 30, noon);
    transferSale.setIdSecurityTransfer(7);
    Transaction transferPurchase = buy(DEPOT_B, stock, chf, 100, 30, noon);
    transferPurchase.setIdSecurityTransfer(7);

    // Handed over purchase first: the view over both security accounts must still show the gain of account A, 2000,
    // and not 1000 from a pool that already holds the units at the closing price
    SecurityTransactionSummary tenantView = run(stock, CostBasisMethods.AVERAGE_ONLY, buyA, transferPurchase,
        transferSale);
    assertThat(gainOf(tenantView, transferSale)).isEqualTo(2_000.0, within(EXACT));
    assertThat(lastGain(run(stock, CostBasisMethods.AVERAGE_ONLY, buyA, transferSale))).isEqualTo(2_000.0,
        within(EXACT));
    assertThat(tenantView.securityPositionSummary.adjustedCostBase).isEqualTo(3_000.0, within(EXACT));
  }

  @Test
  @DisplayName("A partial transfer consumes the lots of the source and opens one new lot in the target")
  void partialTransferWithSeveralLots() {
    Security stock = stock("CHF");
    Cashaccount chf = cashaccount("CHF");
    LocalDateTime noon = LocalDate.of(2024, 3, 1).atTime(12, 0);
    Transaction transferSale = sell(DEPOT_A, stock, chf, 150, 30, noon);
    transferSale.setIdSecurityTransfer(7);
    Transaction transferPurchase = buy(DEPOT_B, stock, chf, 150, 30, noon);
    transferPurchase.setIdSecurityTransfer(7);

    SecurityTransactionSummary summary = run(stock, CostBasisMethods.all(CostBasisMethod.FIFO),
        buy(DEPOT_A, stock, chf, 100, 10, day(1)), buy(DEPOT_A, stock, chf, 100, 20, day(2)), transferPurchase,
        transferSale);

    SecurityTransactionPosition sale = summary.transactionPositionList.stream()
        .filter(row -> row.transaction == transferSale).findFirst().orElseThrow();
    assertThat(sale.consumedLots).extracting(lot -> lot.units).containsExactly(100.0, 50.0);
    assertThat(sale.consumedLots).extracting(lot -> lot.cost).containsExactly(1_000.0, 1_000.0);
    assertThat(sale.transactionGainLoss).isEqualTo(2_500.0, within(EXACT));
    SecurityPositionSummary position = summary.securityPositionSummary;
    assertThat(position.getCostLedger(DEPOT_A).getLots()).extracting(lot -> lot.cost).containsExactly(1_000.0);
    // The target starts over: one lot at the closing price with the date of the transfer
    List<CostLot> target = position.getCostLedger(DEPOT_B).getLots();
    assertThat(target).extracting(lot -> lot.idTransaction).containsExactly(transferPurchase.getIdTransaction());
    assertThat(target.getFirst().transactionTime).isEqualTo(noon);
    assertThat(target.getFirst().cost).isEqualTo(4_500.0, within(EXACT));
  }

  // Buy 100 at 10, buy 100 at 20, dividend 100, sell 100 at 30, price 25
  @ParameterizedTest(name = "{0}: realized {1}, unrealized {2}")
  @CsvSource({ "AVERAGE_COST, 1500, 1000", "FIFO, 2000, 500", "LIFO, 1000, 1500" })
  @DisplayName("The gain of a position splits into realized, income and unrealized, however it is valued")
  void gainComponents(CostBasisMethod method, double realized, double unrealized) {
    Security stock = stock("CHF");
    Cashaccount chf = cashaccount("CHF");
    Transaction[] transactions = { buy(DEPOT_A, stock, chf, 100, 10, day(1)), buy(DEPOT_A, stock, chf, 100, 20, day(2)),
        dividend(stock, chf, 100, 1, 0, day(3)), sell(DEPOT_A, stock, chf, 100, 30, day(4)) };

    SecurityPositionSummary byPrice = run(stock, CostBasisMethods.all(method), transactions).securityPositionSummary;
    byPrice.calcGainLossByPrice(25.0);
    SecurityPositionSummary bySale = run(stock, CostBasisMethods.all(method), transactions).securityPositionSummary;
    service.createHypotheticalSellTransaction(bySale, 25.0, NO_SPLITS, chfOnly(TODAY), null);

    for (SecurityPositionSummary position : List.of(byPrice, bySale)) {
      assertThat(position.gainLossSecurity).isEqualTo(2_600.0, within(EXACT));
      assertThat(position.getRealizedGainLossSecurity()).isEqualTo(realized);
      assertThat(position.getIncomeGainLossSecurity()).isEqualTo(100.0);
      assertThat(position.getUnrealizedGainLossSecurity()).isEqualTo(unrealized);
    }
  }

  // Buy 10 at 100 paying 20 accrued interest, coupon 50 with 10 tax, sell 5 at 110 receiving 15 accrued interest
  @ParameterizedTest(name = "tax excluded {0}: income {1}")
  @CsvSource({ "false, 35", "true, 45" })
  @DisplayName("Dividends and accrued interest are income, net or gross of tax as the tenant has chosen")
  void incomeWithAndWithoutTax(boolean excludeDivTax, double income) {
    Security bond = security("CHF", AssetclassType.FIXED_INCOME);
    Cashaccount chf = cashaccount("CHF");
    Transaction purchase = buy(DEPOT_A, bond, chf, 10, 100, day(1));
    purchase.setAssetInvestmentValue1(20.0);
    Transaction sale = sell(DEPOT_A, bond, chf, 5, 110, day(3));
    sale.setAssetInvestmentValue1(15.0);
    SecurityTransactionSummary summary = new SecurityTransactionSummary(bond, "CHF", PRECISION);
    service.calcTransactions(bond, excludeDivTax, summary, NO_SPLITS,
        List.of(purchase, dividend(bond, chf, 10, 5, 10, day(2)), sale), TODAY, chfOnly(TODAY),
        CostBasisMethods.all(CostBasisMethod.FIFO));

    SecurityPositionSummary position = summary.securityPositionSummary;
    position.calcGainLossByPrice(108.0);
    // Interest -20 + 15, coupon 50 less 10 tax unless the tenant leaves the tax out
    assertThat(position.getIncomeGainLossSecurity()).isEqualTo(income);
    assertThat(position.getRealizedGainLossSecurity()).isEqualTo(50.0);
    assertThat(position.getUnrealizedGainLossSecurity()).isEqualTo(40.0);
    assertThat(position.gainLossSecurity).isEqualTo(income + 90.0, within(EXACT));
  }

  // USD security, main currency CHF: buy 1 at 100 (rate 0.80), buy 1 at 200 (1.00), sell 1 at 300 (1.10),
  // remaining unit valued at 250 (1.20)
  @Test
  @DisplayName("The components are converted at the rate of the report, the currency result stays the fourth figure")
  void gainComponentsInMainCurrency() {
    Security stock = stock("USD");
    Cashaccount chf = cashaccount("CHF");
    SecurityTransactionSummary summary = new SecurityTransactionSummary(stock, "CHF", PRECISION);
    service.calcTransactions(stock, false, summary, NO_SPLITS,
        List.of(fx(buy(DEPOT_A, stock, chf, 1, 100, day(1)), 0.80), fx(buy(DEPOT_A, stock, chf, 1, 200, day(2)), 1.00),
            fx(sell(DEPOT_A, stock, chf, 1, 300, day(3)), 1.10)),
        TODAY, usdChf(TODAY, 1.20), CostBasisMethods.all(CostBasisMethod.FIFO));

    SecurityPositionSummary position = summary.securityPositionSummary;
    position.calcGainLossByPrice(250.0);
    position.calcMainCurrency(1.20);

    assertThat(position.getGainLossSecurityMC()).isEqualTo(300.0);
    // The sale gained 200 USD: 240 CHF at the rate of the report, not the 220 CHF of the day of the sale
    assertThat(position.getRealizedGainLossSecurityMC()).isEqualTo(240.0);
    assertThat(position.getIncomeGainLossSecurityMC()).isZero();
    assertThat(position.getUnrealizedGainLossSecurityMC()).isEqualTo(60.0);
    assertThat(position.getGainLossCurrencyMC()).isEqualTo(50.0);
  }

  // CFD: open 10 at 100, close 4 at 110, the remaining 6 valued at 120
  @Test
  @DisplayName("A partly closed margin position has no income, its open part is the unrealized result")
  void marginGainComponents() {
    Security cfd = security("CHF", AssetclassType.EQUITIES);
    cfd.getAssetClass().setSpecialInvestmentInstrument(SpecialInvestmentInstruments.CFD);
    Cashaccount chf = cashaccount("CHF");
    Transaction open = buy(DEPOT_A, cfd, chf, 10, 100, day(1));
    open.setAssetInvestmentValue2(1.0);
    open.setCashaccountAmount(0.0);
    Transaction close = sell(DEPOT_A, cfd, chf, 4, 110, day(2));
    close.setAssetInvestmentValue2(1.0);
    close.setConnectedIdTransaction(open.getIdTransaction());
    close.setCashaccountAmount(40.0);

    SecurityPositionSummary position = run(cfd, CostBasisMethods.AVERAGE_ONLY, open, close).securityPositionSummary;
    position.calcGainLossByPrice(120.0);

    assertThat(position.getIncomeGainLossSecurity()).isZero();
    assertThat(position.getUnrealizedGainLossSecurity()).isEqualTo(120.0);
    assertThat(position.getRealizedGainLossSecurity()).isEqualTo(40.0);
    assertThat(position.gainLossSecurity).isEqualTo(160.0, within(EXACT));
  }

  // CHF position: buy 100 at 10, buy 100 at 20, dividend 100, sell 100 at 30, price 25, on FIFO.
  // USD position: buy 1 at 100 (rate 0.80), buy 1 at 200 (1.00), sell 1 at 300 (1.10), price 250 (1.20), on FIFO.
  @Test
  @DisplayName("Currency group and grand total add up the components of their positions")
  void gainComponentsOfGroupsAndGrandTotal() {
    Security chfStock = stock("CHF");
    Cashaccount chf = cashaccount("CHF");
    CostBasisMethods fifo = CostBasisMethods.all(CostBasisMethod.FIFO);
    SecurityPositionSummary inChf = run(chfStock, fifo, buy(DEPOT_A, chfStock, chf, 100, 10, day(1)),
        buy(DEPOT_A, chfStock, chf, 100, 20, day(2)), dividend(chfStock, chf, 100, 1, 0, day(3)),
        sell(DEPOT_A, chfStock, chf, 100, 30, day(4))).securityPositionSummary;
    inChf.calcGainLossByPrice(25.0);
    inChf.calcMainCurrency(1.0);

    Security usdStock = stock("USD");
    SecurityTransactionSummary usdSummary = new SecurityTransactionSummary(usdStock, "CHF", PRECISION);
    service.calcTransactions(usdStock, false, usdSummary, NO_SPLITS,
        List.of(fx(buy(DEPOT_A, usdStock, chf, 1, 100, day(1)), 0.80),
            fx(buy(DEPOT_A, usdStock, chf, 1, 200, day(2)), 1.00),
            fx(sell(DEPOT_A, usdStock, chf, 1, 300, day(3)), 1.10)),
        TODAY, usdChf(TODAY, 1.20), fifo);
    SecurityPositionSummary inUsd = usdSummary.securityPositionSummary;
    inUsd.calcGainLossByPrice(250.0);
    inUsd.calcMainCurrency(1.20);

    SecurityPositionCurrenyGroupSummary chfGroup = new SecurityPositionCurrenyGroupSummary("CHF", 1.0, 2);
    chfGroup.addToGroupSummaryAndCalcGroupTotals(inChf);
    SecurityPositionCurrenyGroupSummary usdGroup = new SecurityPositionCurrenyGroupSummary("USD", 1.20, 2);
    usdGroup.addToGroupSummaryAndCalcGroupTotals(inUsd);
    SecurityPositionGrandSummary grand = new SecurityPositionGrandSummary("CHF", 2);
    grand.calcGrandTotal(chfGroup);
    grand.calcGrandTotal(usdGroup);

    // In USD the sale gained 200 and the remaining unit, bought at 200, is worth 250
    assertThat(usdGroup.getGroupRealizedGainLossSecurity()).isEqualTo(200.0);
    assertThat(usdGroup.getGroupIncomeGainLossSecurity()).isZero();
    assertThat(usdGroup.getGroupUnrealizedGainLossSecurity()).isEqualTo(50.0);
    assertThat(usdGroup.getGroupGainLossSecurity()).isEqualTo(250.0);
    // The same at the rate of the report, 1.20
    assertThat(usdGroup.getGroupRealizedGainLossSecurityMC()).isEqualTo(240.0);
    assertThat(usdGroup.getGroupUnrealizedGainLossSecurityMC()).isEqualTo(60.0);
    assertThat(usdGroup.getGroupGainLossSecurityMC()).isEqualTo(300.0);
    // CHF: realized 2000, income 100, unrealized 500. Together with USD the grand total
    assertThat(grand.getGrandRealizedGainLossSecurityMC()).isEqualTo(2_240.0);
    assertThat(grand.getGrandIncomeGainLossSecurityMC()).isEqualTo(100.0);
    assertThat(grand.getGrandUnrealizedGainLossSecurityMC()).isEqualTo(560.0);
    assertThat(grand.getGrandGainLossSecurityMC()).isEqualTo(2_900.0);
    assertThat(grand.grandGainLossCurrencyMC).isEqualTo(50.0, within(EXACT));
  }

  // Security account A: buy 100 at 10, sell 100 at 30. Security account B: buy 100 at 20. Price 25
  @ParameterizedTest(name = "{0}: the view over both realizes {1}")
  @CsvSource({ "AVERAGE_COST, 1500, 1000", "FIFO, 2000, 500", "LIFO, 2000, 500" })
  @DisplayName("The gain of a view over two security accounts is the sum of their views, its realized part only "
      + "with FIFO and LIFO")
  void viewOverSecurityAccountsAgainstTheSumOfTheirViews(CostBasisMethod method, double realizedOfBoth,
      double unrealizedOfBoth) {
    Security stock = stock("CHF");
    Cashaccount chf = cashaccount("CHF");
    CostBasisMethods methods = CostBasisMethods.all(method);
    Transaction buyA = buy(DEPOT_A, stock, chf, 100, 10, day(1));
    Transaction buyB = buy(DEPOT_B, stock, chf, 100, 20, day(2));
    Transaction sellA = sell(DEPOT_A, stock, chf, 100, 30, day(3));

    SecurityPositionSummary both = run(stock, methods, buyA, buyB, sellA).securityPositionSummary;
    SecurityPositionSummary onlyA = run(stock, methods, buyA, sellA).securityPositionSummary;
    SecurityPositionSummary onlyB = run(stock, methods, buyB).securityPositionSummary;
    // A closed position is not valued, as in the reports
    List.of(both, onlyB).forEach(position -> position.calcGainLossByPrice(25.0));

    assertThat(onlyA.getRealizedGainLossSecurity()).isEqualTo(2_000.0);
    assertThat(onlyA.getUnrealizedGainLossSecurity()).isZero();
    assertThat(onlyB.getRealizedGainLossSecurity()).isZero();
    assertThat(onlyB.getUnrealizedGainLossSecurity()).isEqualTo(500.0);
    assertThat(both.gainLossSecurity).isEqualTo(2_500.0, within(EXACT));
    assertThat(both.getRealizedGainLossSecurity()).isEqualTo(realizedOfBoth);
    assertThat(both.getUnrealizedGainLossSecurity()).isEqualTo(unrealizedOfBoth);
  }

  @Test
  @DisplayName("The lots a sale consumed add up to its cost")
  void consumedLotsAddUpToTheCostOfTheSale() {
    Security stock = stock("CHF");
    Cashaccount chf = cashaccount("CHF");
    SecurityTransactionSummary summary = run(stock, CostBasisMethods.all(CostBasisMethod.LIFO),
        buy(DEPOT_A, stock, chf, 100, 10, day(1)), buy(DEPOT_A, stock, chf, 100, 20, day(2)),
        sell(DEPOT_A, stock, chf, 150, 30, day(3)));

    SecurityTransactionPosition sale = summary.transactionPositionList.getLast();
    assertThat(sale.consumedLots).extracting(lot -> lot.idTransaction).containsExactly(2, 1);
    assertThat(sale.consumedLots).extracting(lot -> lot.units).containsExactly(100.0, 50.0);
    assertThat(sale.consumedLots).extracting(lot -> lot.cost).containsExactly(2_000.0, 500.0);
    assertThat(sale.consumedLots.stream().mapToDouble(lot -> lot.cost).sum())
        .isEqualTo(summary.securityPositionSummary.transactionCostBase, within(EXACT));
  }

  // ---------------------------------------------------------------------------------------------------------------

  private SecurityTransactionSummary run(Security security, CostBasisMethods methods, Transaction... transactions) {
    return run(security, methods, TODAY, NO_SPLITS, transactions);
  }

  private SecurityTransactionSummary run(Security security, CostBasisMethods methods, LocalDate untilDate,
      Map<Integer, List<Securitysplit>> splits, Transaction... transactions) {
    SecurityTransactionSummary summary = new SecurityTransactionSummary(security, "CHF", PRECISION);
    service.calcTransactions(security, false, summary, splits, Arrays.asList(transactions), untilDate,
        chfOnly(untilDate), methods);
    return summary;
  }

  private static double lastGain(SecurityTransactionSummary summary) {
    return summary.transactionPositionList.getLast().transactionGainLoss;
  }

  private static double gainOf(SecurityTransactionSummary summary, Transaction transaction) {
    return summary.transactionPositionList.stream().filter(row -> row.transaction == transaction).findFirst()
        .orElseThrow().transactionGainLoss;
  }

  private static DateTransactionCurrencypairMap chfOnly(LocalDate untilDate) {
    return new DateTransactionCurrencypairMap("CHF", untilDate, List.of(), List.of(), true, true, TODAY);
  }

  private static DateTransactionCurrencypairMap usdChf(LocalDate untilDate, double last) {
    Currencypair usdChf = new Currencypair("USD", "CHF");
    usdChf.setIdSecuritycurrency(ID_USDCHF);
    usdChf.setSLast(last);
    return new DateTransactionCurrencypairMap("CHF", untilDate, List.of(), List.of(usdChf), true, true, TODAY);
  }

  private static Map<Integer, List<Securitysplit>> splits(Security security, LocalDate splitDate, int fromFactor,
      int toFactor) {
    return Map.of(security.getIdSecuritycurrency(), List.of(new Securitysplit(security.getIdSecuritycurrency(),
        splitDate, fromFactor, toFactor, CreateType.ADD_MODIFIED_USER)));
  }

  private static Security stock(String currency) {
    return security(currency, AssetclassType.EQUITIES);
  }

  private static Security security(String currency, AssetclassType assetclassType) {
    Assetclass assetclass = new Assetclass();
    assetclass.setCategoryType(assetclassType);
    assetclass.setSpecialInvestmentInstrument(SpecialInvestmentInstruments.DIRECT_INVESTMENT);
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

  private Transaction dividend(Security security, Cashaccount cashaccount, double units, double perUnit,
      double taxCost, LocalDateTime time) {
    return withId(nextId++, new Transaction(DEPOT_A, cashaccount, security, units, perUnit, TransactionType.DIVIDEND,
        taxCost, null, null, time, null));
  }

  private static Transaction withId(Integer idTransaction, Transaction transaction) {
    transaction.setIdTransaction(idTransaction);
    return transaction;
  }

  /** Marks a trade of a USD security settled over a CHF account with the booked exchange rate. */
  private static Transaction fx(Transaction transaction, double exchangeRate) {
    transaction.setIdCurrencypair(ID_USDCHF);
    transaction.setCurrencyExRate(exchangeRate);
    return transaction;
  }
}
