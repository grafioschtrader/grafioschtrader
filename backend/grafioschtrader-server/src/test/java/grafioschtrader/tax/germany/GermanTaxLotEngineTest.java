package grafioschtrader.tax.germany;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.germantax.GermanTaxDetail;
import grafioschtrader.reportviews.germantax.GermanTaxKeys;
import grafioschtrader.reportviews.germantax.GermanTaxSum;
import grafioschtrader.reportviews.germantax.GermanTaxYearReport;
import grafioschtrader.reportviews.germantax.GermanTaxYearReport.PortfolioAssignment;
import grafioschtrader.tax.germany.GermanTaxMarketData.Distribution;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.FundTaxClass;
import grafioschtrader.types.SecurityActionRole;
import grafioschtrader.types.SpecialInvestmentInstruments;
import grafioschtrader.types.SpinOffTaxTreatmentDe;
import grafioschtrader.types.TaxWithholding;
import grafioschtrader.types.TransactionType;

/**
 * The German tax help on real domain objects. Every expected amount is calculated by hand. The market data and the
 * portfolios are small fakes, so each case states exactly the rates, prices and base interest rates it depends on.
 */
@DisplayName("German tax help")
class GermanTaxLotEngineTest {

  private static final double EXACT = 1e-6;
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);
  private static final int DEPOT_A = 11;
  private static final int DEPOT_B = 12;
  private static final int DEPOT_C = 13;

  private FakeMarket market;
  private FakePortfolios portfolios;
  private int nextId;
  private int nextSecurityId;

  @BeforeEach
  void setUp() {
    market = new FakeMarket();
    portfolios = new FakePortfolios();
    nextId = 1;
    nextSecurityId = 1;
  }

  // ------------------------------------------------------------------------------------------ spin-off

  // Parent lots A 60 at 50 (2008, pre-2009) and B 40 at 70, spin-off 4:1 on 2 March 2026, German allocation 20 %
  @Test
  @DisplayName("Spin-off as cost split: 20 % of each parent lot moves to a child lot with the parent's purchase time")
  void spinOffCostSplit() {
    Security parent = stock("EUR");
    Security child = stock("EUR");
    Cashaccount eur = cashaccount("EUR");
    Transaction lotA = buy(DEPOT_A, parent, eur, 60, 50, LocalDateTime.of(2008, 5, 2, 10, 0));
    Transaction lotB = buy(DEPOT_A, parent, eur, 40, 70, LocalDateTime.of(2015, 3, 10, 10, 0));
    Transaction[] legs = spinOffLegs(parent, child, eur, 100, 25);
    // 50 parent units from A: cost 2400 x 50 / 60 = 2000; 20 child units: 15 from A' (600) and 5 from B' (280)
    Transaction parentSale = sell(DEPOT_A, parent, eur, 50, 70, at(2026, 6));
    Transaction childSale = sell(DEPOT_A, child, eur, 20, 50, at(2026, 7));

    GermanTaxYearReport report = runWithSpinOff(SpinOffTaxTreatmentDe.COST_SPLIT, 2026, lotA, lotB, legs[0], legs[1],
        legs[2], parentSale, childSale);

    assertThat(report.details).hasSize(3);
    GermanTaxDetail parentLegacy = report.details.get(0);
    assertThat(parentLegacy.block).isEqualTo(GermanTaxKeys.BLOCK_LEGACY);
    assertThat(parentLegacy.costEur).isEqualTo(2_000.0, within(EXACT));
    assertThat(parentLegacy.gainEur).isEqualTo(1_500.0, within(EXACT));
    GermanTaxDetail childRegular = report.details.get(1);
    assertThat(childRegular.block).isEqualTo(GermanTaxKeys.BLOCK_SHARES);
    assertThat(childRegular.costEur).isEqualTo(280.0, within(EXACT));
    assertThat(childRegular.gainEur).isEqualTo(-30.0, within(EXACT));
    assertThat(childRegular.lots.getFirst().idTransaction).isEqualTo(lotB.getIdTransaction());
    assertThat(childRegular.lots.getFirst().acquisitionTime).isEqualTo(lotB.getTransactionTime());
    GermanTaxDetail childLegacy = report.details.get(2);
    assertThat(childLegacy.block).isEqualTo(GermanTaxKeys.BLOCK_LEGACY);
    assertThat(childLegacy.costEur).isEqualTo(600.0, within(EXACT));
    assertThat(childLegacy.gainEur).isEqualTo(150.0, within(EXACT));
    assertThat(childLegacy.lots.getFirst().acquisitionTime).isEqualTo(lotA.getTransactionTime());
    assertThat(sum(report, GermanTaxKeys.BLOCK_SHARES, GermanTaxKeys.LINE_SHARE_LOSSES).amountEur).isEqualTo(30.0,
        within(EXACT));
    assertThat(report.warnings).extracting(w -> w.code).contains(GermanTaxKeys.WARN_SPIN_OFF).doesNotContain(
        GermanTaxKeys.WARN_UNPAIRED_BOOKING, GermanTaxKeys.WARN_SPIN_OFF_UNDETERMINED,
        GermanTaxKeys.WARN_SALE_FROM_INCOMPLETE_LOT);
  }

  // 50 lots of one unit at 0.03 EUR, 20 %: 0.30 EUR move to the child. Rounding each lot's 0.006 to a cent would hand
  // over 0.01 from 49 lots and -0.19 from the last; the cents must instead go to 30 lots, so 49 parent units cost 1.17
  @Test
  @DisplayName("Spin-off as cost split of many small lots: the cents go to the largest remainders, no lot is negative")
  void spinOffCostSplitOfManySmallLots() {
    Security parent = stock("EUR");
    Security child = stock("EUR");
    Cashaccount eur = cashaccount("EUR");
    List<Transaction> transactions = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
      transactions.add(buy(DEPOT_A, parent, eur, 1, 0.03, LocalDateTime.of(2020, 1, 1, 10, 0).plusDays(i)));
    }
    transactions.addAll(List.of(spinOffLegs(parent, child, eur, 50, 50)));
    transactions.add(sell(DEPOT_A, parent, eur, 49, 1, at(2026, 6)));

    GermanTaxYearReport report = runWithSpinOff(SpinOffTaxTreatmentDe.COST_SPLIT, 2026,
        transactions.toArray(Transaction[]::new));

    assertThat(report.details).singleElement().satisfies(sale -> assertThat(sale.costEur).isEqualTo(1.17,
        within(EXACT)));
  }

  // 103 parent units at 4:1 give 25.75 child units; a third of 100 units at 10 leaves a remainder of one cent
  @Test
  @DisplayName("Spin-off: the warning names the fraction 0.75 and the child lot keeps the exact allotted units")
  void spinOffFraction() {
    Security parent = stock("EUR");
    Security child = stock("EUR");
    Cashaccount eur = cashaccount("EUR");
    Transaction lot = buy(DEPOT_A, parent, eur, 103, 50, at(2020, 1));
    Transaction[] legs = spinOffLegs(parent, child, eur, 103, 25.75);
    Transaction childSale = sell(DEPOT_A, child, eur, 25.75, 40, at(2026, 7));

    GermanTaxYearReport report = runWithSpinOff(SpinOffTaxTreatmentDe.COST_SPLIT, 2026, lot, legs[0], legs[1],
        legs[2], childSale);

    assertThat(report.warnings).filteredOn(w -> GermanTaxKeys.WARN_SPIN_OFF.equals(w.code)).singleElement()
        .satisfies(w -> assertThat(w.detail).isEqualTo("0.75"));
    GermanTaxDetail sale = report.details.getFirst();
    assertThat(sale.complete).isTrue();
    assertThat(sale.lots.getFirst().units).isEqualTo(25.75, within(EXACT));
    assertThat(sale.costEur).isEqualTo(1_030.0, within(EXACT));
  }

  // Parent in USD bought for 5000 USD at 0.90 = 4500 EUR; the child keeps 20 % of the euro cost, not a value of 2026
  @Test
  @DisplayName("Spin-off of a dollar share: the child's euro cost is 20 % of the historical euro cost")
  void spinOffKeepsTheHistoricalEuroCost() {
    Security parent = stock("USD");
    Security child = stock("USD");
    Cashaccount eur = cashaccount("EUR");
    market.rate("USD", LocalDate.of(2020, 1, 10), 0.90);
    market.rate("USD", LocalDate.of(2026, 7, 10), 0.80);
    Transaction lot = buy(DEPOT_A, parent, eur, 100, 50, at(2020, 1));
    Transaction[] legs = spinOffLegs(parent, child, eur, 100, 25);
    Transaction childSale = sell(DEPOT_A, child, eur, 25, 60, at(2026, 7));

    GermanTaxYearReport report = runWithSpinOff(SpinOffTaxTreatmentDe.COST_SPLIT, 2026, lot, legs[0], legs[1],
        legs[2], childSale);

    GermanTaxDetail sale = report.details.getFirst();
    assertThat(sale.costEur).isEqualTo(900.0, within(EXACT));
    assertThat(sale.proceedsEur).isEqualTo(1_200.0, within(EXACT));
    assertThat(sale.gainEur).isEqualTo(300.0, within(EXACT));
  }

  @Test
  @DisplayName("Spin-off as distribution: the child value 1200 is a dividend, the parent keeps its cost")
  void spinOffTaxableDistribution() {
    Security parent = stock("EUR");
    Security child = stock("EUR");
    Cashaccount eur = cashaccount("EUR");
    Transaction lotA = buy(DEPOT_A, parent, eur, 60, 50, LocalDateTime.of(2008, 5, 2, 10, 0));
    Transaction lotB = buy(DEPOT_A, parent, eur, 40, 70, LocalDateTime.of(2015, 3, 10, 10, 0));
    Transaction[] legs = spinOffLegs(parent, child, eur, 100, 25);
    Transaction parentSale = sell(DEPOT_A, parent, eur, 50, 70, at(2026, 6));
    Transaction childSale = sell(DEPOT_A, child, eur, 10, 50, at(2026, 7));

    GermanTaxYearReport report = runWithSpinOff(SpinOffTaxTreatmentDe.TAXABLE_DISTRIBUTION, 2026, lotA, lotB,
        legs[0], legs[1], legs[2], parentSale, childSale);

    assertThat(sum(report, GermanTaxKeys.BLOCK_SHARES, GermanTaxKeys.LINE_SHARE_DIVIDENDS).amountEur)
        .isEqualTo(1_200.0, within(EXACT));
    List<GermanTaxDetail> sales = report.details.stream().filter(d -> GermanTaxKeys.KIND_SALE.equals(d.kind))
        .toList();
    // The parent sale takes 50 of lot A at its full cost 3000 x 50 / 60
    assertThat(sales.get(0).costEur).isEqualTo(2_500.0, within(EXACT));
    assertThat(sales.get(0).block).isEqualTo(GermanTaxKeys.BLOCK_LEGACY);
    // The child lot is new: 1200 for 25 units, dated on the action date and therefore no legacy holding
    assertThat(sales.get(1).costEur).isEqualTo(480.0, within(EXACT));
    assertThat(sales.get(1).block).isEqualTo(GermanTaxKeys.BLOCK_SHARES);
    assertThat(sales.get(1).lots.getFirst().acquisitionTime).isEqualTo(legs[2].getTransactionTime());
    assertThat(report.warnings).extracting(w -> w.code).contains(GermanTaxKeys.WARN_SPIN_OFF_DISTRIBUTION);
  }

  @Test
  @DisplayName("Spin-off without tax treatment: split like a cost split, but later sales of both sides have no amount")
  void spinOffUndetermined() {
    Security parent = stock("EUR");
    Security child = stock("EUR");
    Cashaccount eur = cashaccount("EUR");
    Transaction lot = buy(DEPOT_A, parent, eur, 100, 50, at(2020, 1));
    Transaction[] legs = spinOffLegs(parent, child, eur, 100, 25);
    Transaction parentSale = sell(DEPOT_A, parent, eur, 50, 70, at(2026, 6));
    Transaction childSale = sell(DEPOT_A, child, eur, 10, 50, at(2026, 7));

    GermanTaxYearReport report = runWithSpinOff(SpinOffTaxTreatmentDe.UNDETERMINED, 2026, lot, legs[0], legs[1],
        legs[2], parentSale, childSale);

    assertThat(report.details).extracting(d -> d.complete).containsExactly(false, false);
    assertThat(report.details).extracting(d -> d.gainEur).containsOnlyNulls();
    assertThat(sum(report, GermanTaxKeys.BLOCK_SHARES, GermanTaxKeys.LINE_SHARE_GAINS).amountEur).isNull();
    assertThat(report.warnings).extracting(w -> w.code).contains(GermanTaxKeys.WARN_SPIN_OFF_UNDETERMINED)
        .doesNotContain(GermanTaxKeys.WARN_SPIN_OFF);
  }

  @Test
  @DisplayName("Spin-off of a fund: the derived lots are incomplete and a later child sale has no amount")
  void spinOffOfAFund() {
    Security parent = fund("EUR", FundTaxClass.EQUITY_FUND);
    Security child = stock("EUR");
    Cashaccount eur = cashaccount("EUR");
    Transaction lot = buy(DEPOT_A, parent, eur, 100, 50, at(2026, 1));
    Transaction[] legs = spinOffLegs(parent, child, eur, 100, 25);
    Transaction childSale = sell(DEPOT_A, child, eur, 10, 50, at(2026, 7));

    GermanTaxYearReport report = runWithSpinOff(SpinOffTaxTreatmentDe.COST_SPLIT, 2026, lot, legs[0], legs[1],
        legs[2], childSale);

    assertThat(report.details.getFirst().complete).isFalse();
    assertThat(report.warnings).extracting(w -> w.code).contains(GermanTaxKeys.WARN_SPIN_OFF_FUND);
  }

  @Test
  @DisplayName("Spin-off with a missing booking: the child lot is incomplete without the repurchase or the sale")
  void spinOffIncomplete() {
    Security parent = stock("EUR");
    Security child = stock("EUR");
    Cashaccount eur = cashaccount("EUR");
    Transaction lot = buy(DEPOT_A, parent, eur, 100, 50, at(2020, 1));
    Transaction[] legs = spinOffLegs(parent, child, eur, 100, 25);
    Transaction childSale = sell(DEPOT_A, child, eur, 10, 50, at(2026, 7));

    GermanTaxYearReport withoutRepurchase = runWithSpinOff(SpinOffTaxTreatmentDe.COST_SPLIT, 2026, lot, legs[0],
        legs[2], childSale);
    GermanTaxYearReport withoutSale = runWithSpinOff(SpinOffTaxTreatmentDe.COST_SPLIT, 2026, lot, legs[1], legs[2],
        childSale);

    for (GermanTaxYearReport report : List.of(withoutRepurchase, withoutSale)) {
      assertThat(report.details.getFirst().complete).isFalse();
      assertThat(report.warnings).extracting(w -> w.code).contains(GermanTaxKeys.WARN_SPIN_OFF_INCOMPLETE)
          .doesNotContain(GermanTaxKeys.WARN_UNPAIRED_BOOKING);
    }
  }

  // ------------------------------------------------------------------------------------------ gains of sales

  // USD share: buy 10 at 100 with costs 5 at 0.90, sell 10 at 120 with costs 5 at 0.80
  @Test
  @DisplayName("The cost counts at the rate of the purchase, the proceeds at the rate of the sale")
  void currencyEffectBelongsToTheGain() {
    Security stock = stock("USD");
    Cashaccount chf = cashaccount("CHF");
    market.rate("USD", LocalDate.of(2025, 1, 10), 0.90);
    market.rate("USD", LocalDate.of(2025, 3, 10), 0.80);
    Transaction buy = costs(buy(DEPOT_A, stock, chf, 10, 100, at(2025, 1)), 5);
    Transaction sell = costs(sell(DEPOT_A, stock, chf, 10, 120, at(2025, 3)), 5);

    GermanTaxYearReport report = run(2025, buy, sell);

    GermanTaxDetail sale = report.details.getFirst();
    assertThat(sale.costEur).isEqualTo(904.50, within(EXACT));
    assertThat(sale.proceedsEur).isEqualTo(956.00, within(EXACT));
    assertThat(sale.gainEur).isEqualTo(51.50, within(EXACT));
    assertThat(sum(report, GermanTaxKeys.BLOCK_SHARES, GermanTaxKeys.LINE_SHARE_GAINS).amountEur).isEqualTo(51.50,
        within(EXACT));
  }

  @Test
  @DisplayName("Each security account keeps its own FIFO ledger, a partial sale takes a part of the oldest lot")
  void fifoPerSecurityAccount() {
    Security stock = stock("EUR");
    Cashaccount eur = cashaccount("EUR");

    GermanTaxYearReport report = run(2025, buy(DEPOT_A, stock, eur, 100, 10, at(2025, 1)),
        buy(DEPOT_B, stock, eur, 100, 20, at(2025, 2)), buy(DEPOT_A, stock, eur, 100, 30, at(2025, 3)),
        sell(DEPOT_A, stock, eur, 150, 40, at(2025, 4)), sell(DEPOT_B, stock, eur, 50, 40, at(2025, 5)));

    GermanTaxDetail saleA = report.details.getFirst();
    // 100 units at 10 and 50 units at 30: cost 1000 + 1500
    assertThat(saleA.costEur).isEqualTo(2_500.0, within(EXACT));
    assertThat(saleA.lots).extracting(lot -> lot.units).containsExactly(100.0, 50.0);
    GermanTaxDetail saleB = report.details.get(1);
    assertThat(saleB.costEur).isEqualTo(1_000.0, within(EXACT));
    assertThat(sum(report, GermanTaxKeys.BLOCK_SHARES, GermanTaxKeys.LINE_SHARE_GAINS).amountEur)
        .isEqualTo(6_000.0 - 2_500.0 + 2_000.0 - 1_000.0, within(EXACT));
  }

  @Test
  @DisplayName("A share loss and a gain from a bond stay in their own pots, nothing is netted")
  void lossesAreNotNetted() {
    Security stock = stock("EUR");
    Security bond = security("EUR", AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.DIRECT_INVESTMENT);
    Cashaccount eur = cashaccount("EUR");

    GermanTaxYearReport report = run(2025, buy(DEPOT_A, stock, eur, 100, 10, at(2025, 1)),
        sell(DEPOT_A, stock, eur, 100, 5, at(2025, 2)), buy(DEPOT_A, bond, eur, 10, 100, at(2025, 1)),
        sell(DEPOT_A, bond, eur, 10, 110, at(2025, 2)));

    assertThat(sum(report, GermanTaxKeys.BLOCK_SHARES, GermanTaxKeys.LINE_SHARE_LOSSES).amountEur).isEqualTo(500.0,
        within(EXACT));
    assertThat(sumOrNull(report, GermanTaxKeys.BLOCK_SHARES, GermanTaxKeys.LINE_SHARE_GAINS)).isNull();
    assertThat(sum(report, GermanTaxKeys.BLOCK_OTHER_INCOME, GermanTaxKeys.LINE_CLAIM_GAINS).amountEur)
        .isEqualTo(100.0, within(EXACT));
  }

  // Buy 10 at 100 paying 20 accrued interest in 2025, sell 10 at 105 receiving 30 in 2026
  @Test
  @DisplayName("Accrued interest is income of the year of the booking and never part of the gain")
  void accruedInterest() {
    Security bond = security("EUR", AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.DIRECT_INVESTMENT);
    Cashaccount eur = cashaccount("EUR");
    Transaction buy = buy(DEPOT_A, bond, eur, 10, 100, at(2025, 1));
    buy.setAssetInvestmentValue1(20.0);
    Transaction sell = sell(DEPOT_A, bond, eur, 10, 105, at(2026, 3));
    sell.setAssetInvestmentValue1(30.0);

    GermanTaxYearReport year2025 = run(2025, buy, sell);
    GermanTaxYearReport year2026 = run(2026, buy, sell);

    assertThat(sum(year2025, GermanTaxKeys.BLOCK_OTHER_INCOME, GermanTaxKeys.LINE_ACCRUED_INTEREST_PAID).amountEur)
        .isEqualTo(-20.0, within(EXACT));
    assertThat(sum(year2026, GermanTaxKeys.BLOCK_OTHER_INCOME, GermanTaxKeys.LINE_ACCRUED_INTEREST_RECEIVED)
        .amountEur).isEqualTo(30.0, within(EXACT));
    assertThat(sum(year2026, GermanTaxKeys.BLOCK_OTHER_INCOME, GermanTaxKeys.LINE_CLAIM_GAINS).amountEur)
        .isEqualTo(50.0, within(EXACT));
  }

  // ------------------------------------------------------------------------------------------ advance lump sum

  // Equity fund in euro: buy 100 at 100 on 10 March 2025, prices of 2025 100 and 110, base rate 2.53 %
  @Test
  @DisplayName("The advance lump sum of a year is income of the next year and reduces a later sale in full")
  void advanceLumpSumAndSale() {
    Security fund = fund("EUR", FundTaxClass.EQUITY_FUND);
    Cashaccount eur = cashaccount("EUR");
    market.baseRate(2025, 2.53);
    market.fundYear(fund, 2025, 100, 110);
    Transaction buy = buy(DEPOT_A, fund, eur, 100, 100, at(2025, 3));
    Transaction sell = sell(DEPOT_A, fund, eur, 100, 130, LocalDateTime.of(2026, 6, 1, 10, 0));

    GermanTaxYearReport report = run(2026, buy, sell);

    GermanTaxSum prepayment = sum(report, GermanTaxKeys.BLOCK_FUNDS, GermanTaxKeys.LINE_FUND_PRELIMINARY,
        FundTaxClass.EQUITY_FUND);
    assertThat(prepayment.amountEur).isEqualTo(147.58, within(EXACT));
    assertThat(prepayment.amountAfterPartialExemptionEur).isEqualTo(103.31, within(EXACT));
    GermanTaxSum disposal = sum(report, GermanTaxKeys.BLOCK_FUNDS, GermanTaxKeys.LINE_FUND_DISPOSAL,
        FundTaxClass.EQUITY_FUND);
    assertThat(disposal.amountEur).isEqualTo(2_852.42, within(EXACT));
    assertThat(disposal.amountAfterPartialExemptionEur).isEqualTo(1_996.69, within(EXACT));
    assertThat(report.details).filteredOn(d -> GermanTaxKeys.KIND_PRELIMINARY.equals(d.kind)).singleElement()
        .satisfies(d -> assertThat(d.date).isEqualTo(LocalDate.of(2026, 1, 2)));
  }

  @Test
  @DisplayName("A fund held at the year end but sold on 2 January still has its advance lump sum as income")
  void soldAfterTheYearEnd() {
    Security fund = fund("EUR", FundTaxClass.EQUITY_FUND);
    Cashaccount eur = cashaccount("EUR");
    market.baseRate(2025, 2.53);
    market.fundYear(fund, 2025, 100, 110);

    GermanTaxYearReport report = run(2026, buy(DEPOT_A, fund, eur, 100, 100, at(2025, 3)),
        sell(DEPOT_A, fund, eur, 100, 110, LocalDateTime.of(2026, 1, 2, 10, 0)));

    assertThat(sum(report, GermanTaxKeys.BLOCK_FUNDS, GermanTaxKeys.LINE_FUND_PRELIMINARY, FundTaxClass.EQUITY_FUND)
        .amountEur).isEqualTo(147.58, within(EXACT));
    // 1000 gain less the advance lump sum of 147.5833, rounded to cents
    assertThat(sum(report, GermanTaxKeys.BLOCK_FUNDS, GermanTaxKeys.LINE_FUND_DISPOSAL, FundTaxClass.EQUITY_FUND)
        .amountEur).isEqualTo(852.42, within(EXACT));
  }

  @Test
  @DisplayName("The first working day of 2027 is 4 January")
  void firstWorkingDay() {
    assertThat(GermanTaxLotEngine.firstWorkingDay(2027)).isEqualTo(LocalDate.of(2027, 1, 4));
    assertThat(GermanTaxLotEngine.firstWorkingDay(2026)).isEqualTo(LocalDate.of(2026, 1, 2));
    assertThat(GermanTaxLotEngine.firstWorkingDay(2023)).isEqualTo(LocalDate.of(2023, 1, 2));
  }

  // USD fund, price 100 USD all year, one unit bought in January so the whole year counts
  @Test
  @DisplayName("Prices convert to euro on their days: a falling dollar gives no advance lump sum, a rising one does")
  void foreignCurrencyFund() {
    Security fund = fund("USD", FundTaxClass.EQUITY_FUND);
    Cashaccount eur = cashaccount("EUR");
    market.baseRate(2025, 2.53);
    market.fundYear(fund, 2025, 100, 100);
    market.rate("USD", LocalDate.of(2025, 1, 2), 0.90);
    market.rate("USD", LocalDate.of(2025, 12, 30), 0.80);
    market.rate("USD", LocalDate.of(2025, 1, 5), 0.90);
    Transaction buy = buy(DEPOT_A, fund, eur, 100, 100, LocalDateTime.of(2025, 1, 5, 10, 0));

    GermanTaxYearReport falling = run(2026, buy);
    assertThat(sum(falling, GermanTaxKeys.BLOCK_FUNDS, GermanTaxKeys.LINE_FUND_PRELIMINARY,
        FundTaxClass.EQUITY_FUND).amountEur).isZero();

    market.rate("USD", LocalDate.of(2025, 1, 2), 0.80);
    market.rate("USD", LocalDate.of(2025, 12, 30), 0.90);
    GermanTaxYearReport rising = run(2026, buy);
    // 80 EUR at the start * 2.53 % * 0.7 = 1.4168 per unit
    assertThat(sum(rising, GermanTaxKeys.BLOCK_FUNDS, GermanTaxKeys.LINE_FUND_PRELIMINARY, FundTaxClass.EQUITY_FUND)
        .amountEur).isEqualTo(141.68, within(EXACT));
  }

  // Base income 2 per unit, increase in value 1, distribution 0.50
  @Test
  @DisplayName("The advance lump sum is limited by the increase in value and reduced by the distributions")
  void yearlyLimit() {
    Security fund = fund("EUR", FundTaxClass.EQUITY_FUND);
    Cashaccount eur = cashaccount("EUR");
    market.baseRate(2025, 200.0 / 70.0);
    market.fundYear(fund, 2025, 100, 101, new Distribution(LocalDate.of(2025, 6, 15), 0.5, "EUR"));

    GermanTaxYearReport report = run(2026, buy(DEPOT_A, fund, eur, 1, 100, LocalDateTime.of(2025, 1, 5, 10, 0)));

    assertThat(sum(report, GermanTaxKeys.BLOCK_FUNDS, GermanTaxKeys.LINE_FUND_PRELIMINARY, FundTaxClass.EQUITY_FUND)
        .amountEur).isEqualTo(1.0, within(EXACT));
  }

  @Test
  @DisplayName("A distribution above the base income leaves no advance lump sum")
  void distributionAboveBaseIncome() {
    Security fund = fund("EUR", FundTaxClass.EQUITY_FUND);
    Cashaccount eur = cashaccount("EUR");
    market.baseRate(2025, 2.53);
    market.fundYear(fund, 2025, 100, 110, new Distribution(LocalDate.of(2025, 6, 15), 5.0, "EUR"));

    GermanTaxYearReport report = run(2026, buy(DEPOT_A, fund, eur, 100, 100, LocalDateTime.of(2025, 1, 5, 10, 0)));

    assertThat(sum(report, GermanTaxKeys.BLOCK_FUNDS, GermanTaxKeys.LINE_FUND_PRELIMINARY, FundTaxClass.EQUITY_FUND)
        .amountEur).isZero();
  }

  @Test
  @DisplayName("Without a price at the start of the year the advance lump sum stays open and so does a later sale")
  void historyStartsInJune() {
    Security fund = fund("EUR", FundTaxClass.EQUITY_FUND);
    Cashaccount eur = cashaccount("EUR");
    market.baseRate(2025, 2.53);
    market.fundYearWithoutFirstPrice(fund, 2025, 110);
    Transaction buy = buy(DEPOT_A, fund, eur, 100, 100, at(2025, 3));
    Transaction sell = sell(DEPOT_A, fund, eur, 100, 130, at(2026, 6));

    GermanTaxYearReport report = run(2026, buy, sell);

    GermanTaxSum prepayment = sum(report, GermanTaxKeys.BLOCK_FUNDS, GermanTaxKeys.LINE_FUND_PRELIMINARY,
        FundTaxClass.EQUITY_FUND);
    assertThat(prepayment.complete).isFalse();
    assertThat(prepayment.amountEur).isNull();
    GermanTaxSum disposal = sum(report, GermanTaxKeys.BLOCK_FUNDS, GermanTaxKeys.LINE_FUND_DISPOSAL,
        FundTaxClass.EQUITY_FUND);
    assertThat(disposal.complete).isFalse();
    assertThat(disposal.amountEur).isNull();
    assertThat(report.warnings).extracting(w -> w.code).contains(GermanTaxKeys.WARN_MISSING_PRICE);
  }

  @Test
  @DisplayName("A fund without any distribution record is not taken as distribution free")
  void distributionsUnknown() {
    Security fund = fund("EUR", FundTaxClass.EQUITY_FUND);
    Cashaccount eur = cashaccount("EUR");
    market.baseRate(2025, 2.53);
    market.fundYearWithUnknownDistributions(fund, 2025, 100, 110);

    GermanTaxYearReport report = run(2026, buy(DEPOT_A, fund, eur, 100, 100, at(2025, 3)));

    GermanTaxSum prepayment = sum(report, GermanTaxKeys.BLOCK_FUNDS, GermanTaxKeys.LINE_FUND_PRELIMINARY,
        FundTaxClass.EQUITY_FUND);
    assertThat(prepayment.amountEur).isNull();
    assertThat(report.warnings).extracting(w -> w.code).contains(GermanTaxKeys.WARN_MISSING_DISTRIBUTIONS);
  }

  @Test
  @DisplayName("A missing base interest rate of a year in the holding period keeps the sale out of every sum")
  void missingBaseRateInHoldingPeriod() {
    Security fund = fund("EUR", FundTaxClass.EQUITY_FUND);
    Cashaccount eur = cashaccount("EUR");
    for (int year = 2020; year <= 2025; year++) {
      market.baseRate(year, 2.0);
      market.fundYear(fund, year, 100, 110);
    }
    market.fundYear(fund, 2019, 100, 110);

    GermanTaxYearReport report = run(2026, buy(DEPOT_A, fund, eur, 100, 100, at(2019, 5)),
        sell(DEPOT_A, fund, eur, 100, 150, at(2026, 6)));

    GermanTaxSum disposal = sum(report, GermanTaxKeys.BLOCK_FUNDS, GermanTaxKeys.LINE_FUND_DISPOSAL,
        FundTaxClass.EQUITY_FUND);
    assertThat(disposal.complete).isFalse();
    assertThat(report.warnings).extracting(w -> w.code).contains(GermanTaxKeys.WARN_MISSING_BASE_RATE,
        GermanTaxKeys.WARN_SALE_FROM_INCOMPLETE_LOT);
    assertThat(report.warnings.stream().filter(w -> GermanTaxKeys.WARN_MISSING_BASE_RATE.equals(w.code)).findFirst()
        .orElseThrow().affectedSums).contains(disposal.key());
  }

  // ------------------------------------------------------------------------------------------ income

  @Test
  @DisplayName("A foreign dividend converts at the rate of its day, the creditable ceiling is 25 % of the income")
  void foreignDividendAndTax() {
    Security stock = stock("USD");
    Cashaccount eur = cashaccount("EUR");
    market.rate("USD", LocalDate.of(2025, 5, 10), 0.90);
    Transaction dividend = dividend(DEPOT_A, stock, eur, 1, 100, 30, at(2025, 5));

    GermanTaxYearReport report = run(2025, buy(DEPOT_A, stock, eur, 1, 100, at(2025, 1)), dividend);

    assertThat(sum(report, GermanTaxKeys.BLOCK_SHARES, GermanTaxKeys.LINE_SHARE_DIVIDENDS).amountEur)
        .isEqualTo(90.0, within(EXACT));
    assertThat(sum(report, GermanTaxKeys.BLOCK_FOREIGN_TAX, GermanTaxKeys.LINE_TAX_RECORDED).amountEur)
        .isEqualTo(27.0, within(EXACT));
    assertThat(sum(report, GermanTaxKeys.BLOCK_FOREIGN_TAX, GermanTaxKeys.LINE_TAX_CEILING).amountEur)
        .isEqualTo(22.5, within(EXACT));
    assertThat(sum(report, GermanTaxKeys.BLOCK_FOREIGN_TAX, GermanTaxKeys.LINE_TAX_NOT_CREDITED).amountEur)
        .isEqualTo(4.5, within(EXACT));
  }

  @Test
  @DisplayName("The ceiling of a fund distribution is taken after the partial exemption")
  void fundTaxCeiling() {
    Security fund = fund("EUR", FundTaxClass.FOREIGN_REAL_ESTATE_FUND);
    Cashaccount eur = cashaccount("EUR");

    GermanTaxYearReport report = run(2025, buy(DEPOT_A, fund, eur, 1, 100, at(2025, 1)),
        dividend(DEPOT_A, fund, eur, 1, 10_000, 1_500, at(2025, 5)));

    assertThat(sum(report, GermanTaxKeys.BLOCK_FOREIGN_TAX, GermanTaxKeys.LINE_TAX_CEILING).amountEur)
        .isEqualTo(500.0, within(EXACT));
    GermanTaxSum distribution = sum(report, GermanTaxKeys.BLOCK_FUNDS, GermanTaxKeys.LINE_FUND_DISTRIBUTIONS,
        FundTaxClass.FOREIGN_REAL_ESTATE_FUND);
    assertThat(distribution.amountEur).isEqualTo(10_000.0, within(EXACT));
    assertThat(distribution.amountAfterPartialExemptionEur).isEqualTo(2_000.0, within(EXACT));
  }

  @Test
  @DisplayName("A tax of 35 on 100 has a ceiling line and no line called creditable")
  void taxCeilingIsNamedCeiling() {
    Security stock = stock("EUR");
    Cashaccount eur = cashaccount("EUR");

    GermanTaxYearReport report = run(2025, buy(DEPOT_A, stock, eur, 1, 100, at(2025, 1)),
        dividend(DEPOT_A, stock, eur, 1, 100, 35, at(2025, 5)));

    assertThat(report.sums).extracting(s -> s.line).contains(GermanTaxKeys.LINE_TAX_RECORDED,
        GermanTaxKeys.LINE_TAX_CEILING, GermanTaxKeys.LINE_TAX_NOT_CREDITED);
    assertThat(sum(report, GermanTaxKeys.BLOCK_FOREIGN_TAX, GermanTaxKeys.LINE_TAX_CEILING).amountEur)
        .isEqualTo(25.0, within(EXACT));
  }

  @Test
  @DisplayName("A dividend without the rate of its currency has no amount and no factor 1")
  void dividendWithoutRate() {
    Security stock = stock("USD");
    Cashaccount eur = cashaccount("USD");

    GermanTaxYearReport report = run(2025, dividend(DEPOT_A, stock, eur, 1, 100, 30, at(2025, 5)));

    GermanTaxSum income = sum(report, GermanTaxKeys.BLOCK_SHARES, GermanTaxKeys.LINE_SHARE_DIVIDENDS);
    assertThat(income.complete).isFalse();
    assertThat(income.amountEur).isNull();
    assertThat(sum(report, GermanTaxKeys.BLOCK_FOREIGN_TAX, GermanTaxKeys.LINE_TAX_RECORDED).amountEur).isNull();
    assertThat(report.warnings).extracting(w -> w.code).contains(GermanTaxKeys.WARN_MISSING_RATE);
  }

  @Test
  @DisplayName("Interest of a foreign cash account converts with the rate of the account currency")
  void cashAccountInterest() {
    Cashaccount usd = cashaccount("USD");
    market.rate("USD", LocalDate.of(2025, 6, 10), 0.90);
    Transaction interest = new Transaction(usd, 100.0, TransactionType.INTEREST_CASHACCOUNT, at(2025, 6));
    withDate(interest);

    GermanTaxYearReport report = new GermanTaxLotEngine(market, portfolios, Map.of(), TODAY).calculate(2025, List.of(),
        List.of(interest));

    assertThat(sum(report, GermanTaxKeys.BLOCK_OTHER_INCOME, GermanTaxKeys.LINE_INTEREST).amountEur).isEqualTo(90.0,
        within(EXACT));
  }

  // ------------------------------------------------------------------------------------------ moves

  @Test
  @DisplayName("A transfer moves the lots with their cost and time and realizes no gain")
  void transferRealizesNothing() {
    Security stock = stock("EUR");
    Cashaccount eur = cashaccount("EUR");
    LocalDateTime transferTime = LocalDateTime.of(2024, 12, 31, 12, 0);
    Transaction[] transfer = transfer(stock, eur, DEPOT_A, DEPOT_B, 100, 20, transferTime, 1);
    Transaction buy = buy(DEPOT_A, stock, eur, 100, 10, at(2024, 1));
    Transaction sell = sell(DEPOT_B, stock, eur, 100, 30, at(2025, 6));

    GermanTaxYearReport year2024 = run(2024, buy, transfer[0], transfer[1], sell);
    GermanTaxYearReport year2025 = run(2025, buy, transfer[0], transfer[1], sell);

    assertThat(year2024.sums).isEmpty();
    // The cost of the original purchase, not the closing price of the transfer
    assertThat(sum(year2025, GermanTaxKeys.BLOCK_SHARES, GermanTaxKeys.LINE_SHARE_GAINS).amountEur)
        .isEqualTo(2_000.0, within(EXACT));
    assertThat(year2025.details.getFirst().lots.getFirst().idTransaction).isEqualTo(buy.getIdTransaction());
  }

  @Test
  @DisplayName("A partial transfer splits the lots and a security account with a younger lot sells the older one first")
  void partialTransferAndOrder() {
    Security stock = stock("EUR");
    Cashaccount eur = cashaccount("EUR");
    LocalDateTime transferTime = LocalDateTime.of(2024, 12, 31, 12, 0);
    Transaction[] transfer = transfer(stock, eur, DEPOT_A, DEPOT_B, 150, 20, transferTime, 1);
    Transaction youngerInB = buy(DEPOT_B, stock, eur, 100, 50, at(2024, 6));

    GermanTaxYearReport report = run(2025, buy(DEPOT_A, stock, eur, 100, 10, at(2024, 1)),
        buy(DEPOT_A, stock, eur, 100, 20, at(2024, 2)), youngerInB, transfer[0], transfer[1],
        sell(DEPOT_B, stock, eur, 200, 30, at(2025, 6)));

    GermanTaxDetail sale = report.details.getFirst();
    // The transferred lots are older than the lot of B: 100 at 10, 50 at 20, then 50 at 50 from the younger lot
    assertThat(sale.lots).extracting(lot -> lot.units).containsExactly(100.0, 50.0, 50.0);
    assertThat(sale.costEur).isEqualTo(1_000.0 + 1_000.0 + 2_500.0, within(EXACT));
  }

  @Test
  @DisplayName("Two chained transfers of the same minute resolve, whatever their order in the database")
  void chainedTransfers() {
    Security stock = stock("EUR");
    Cashaccount eur = cashaccount("EUR");
    LocalDateTime transferTime = LocalDateTime.of(2024, 12, 31, 12, 0);
    // B to C is booked first and finds nothing in B until the transfer from A to B has run
    Transaction[] bToC = transfer(stock, eur, DEPOT_B, DEPOT_C, 100, 20, transferTime, 1);
    Transaction[] aToB = transfer(stock, eur, DEPOT_A, DEPOT_B, 100, 20, transferTime, 2);

    GermanTaxYearReport report = run(2025, buy(DEPOT_A, stock, eur, 100, 10, at(2024, 1)), bToC[0], bToC[1], aToB[0],
        aToB[1], sell(DEPOT_C, stock, eur, 100, 30, at(2025, 6)));

    assertThat(sum(report, GermanTaxKeys.BLOCK_SHARES, GermanTaxKeys.LINE_SHARE_GAINS).amountEur).isEqualTo(2_000.0,
        within(EXACT));
    assertThat(report.warnings).isEmpty();
  }

  // Equity fund, 100 units bought on 10 March 2025, 40 of them transferred on 1 June
  @Test
  @DisplayName("A partial transfer neither doubles nor loses the advance lump sum at the year end")
  void advanceLumpSumAfterPartialTransfer() {
    Security fund = fund("EUR", FundTaxClass.EQUITY_FUND);
    Cashaccount eur = cashaccount("EUR");
    market.baseRate(2025, 2.53);
    market.fundYear(fund, 2025, 100, 110);
    Transaction[] transfer = transfer(fund, eur, DEPOT_A, DEPOT_B, 40, 105, LocalDateTime.of(2025, 6, 1, 12, 0), 1);

    GermanTaxYearReport report = run(2026, buy(DEPOT_A, fund, eur, 100, 100, at(2025, 3)), transfer[0], transfer[1]);

    assertThat(sum(report, GermanTaxKeys.BLOCK_FUNDS, GermanTaxKeys.LINE_FUND_PRELIMINARY, FundTaxClass.EQUITY_FUND)
        .amountEur).isEqualTo(147.58, within(EXACT));
  }

  @Test
  @DisplayName("The block of a sale follows the portfolio of the sale, the cost is the one that moved along")
  void blockFollowsThePortfolioOfTheSale() {
    Security stock = stock("EUR");
    Cashaccount eur = cashaccount("EUR");
    portfolios.assign(DEPOT_B, 2, TaxWithholding.DOMESTIC_WITHHOLDING);
    LocalDateTime transferTime = LocalDateTime.of(2024, 12, 31, 12, 0);
    Transaction[] transfer = transfer(stock, eur, DEPOT_A, DEPOT_B, 100, 20, transferTime, 1);

    GermanTaxYearReport report = run(2025, buy(DEPOT_A, stock, eur, 100, 10, at(2024, 1)), transfer[0], transfer[1],
        sell(DEPOT_B, stock, eur, 100, 30, at(2025, 6)));

    assertThat(sumOrNull(report, GermanTaxKeys.BLOCK_SHARES, GermanTaxKeys.LINE_SHARE_GAINS)).isNull();
    GermanTaxSum withheld = sum(report, GermanTaxKeys.BLOCK_WITHHELD, GermanTaxKeys.LINE_SHARE_GAINS);
    assertThat(withheld.category).isEqualTo(GermanTaxKeys.CATEGORY_SHARES);
    assertThat(withheld.amountEur).isEqualTo(2_000.0, within(EXACT));
  }

  @Test
  @DisplayName("A portfolio that is not assigned yet puts its values in no form and asks for the assignment")
  void unassignedPortfolio() {
    Security stock = stock("EUR");
    Cashaccount eur = cashaccount("EUR");
    portfolios.assign(DEPOT_A, 1, null);

    GermanTaxYearReport report = run(2025, buy(DEPOT_A, stock, eur, 100, 10, at(2025, 1)),
        sell(DEPOT_A, stock, eur, 100, 20, at(2025, 2)));

    assertThat(sumOrNull(report, GermanTaxKeys.BLOCK_SHARES, GermanTaxKeys.LINE_SHARE_GAINS)).isNull();
    assertThat(sum(report, GermanTaxKeys.BLOCK_UNREVIEWED, GermanTaxKeys.LINE_SHARE_GAINS).amountEur)
        .isEqualTo(1_000.0, within(EXACT));
    assertThat(report.portfolioAssignmentRequired).isTrue();
  }

  // Old share 100 at 10, ISIN change in the ratio 1:2, later sale of 200 new units at 10
  @Test
  @DisplayName("An ISIN change continues the lots with the converted units, cost and purchase time stay")
  void isinChange() {
    Security oldStock = stock("EUR");
    Security newStock = stock("EUR");
    Cashaccount eur = cashaccount("EUR");
    LocalDateTime changeTime = LocalDateTime.of(2024, 6, 1, 12, 0);
    Transaction sellOld = sell(DEPOT_A, oldStock, eur, 100, 12, changeTime);
    Transaction buyNew = buy(DEPOT_A, newStock, eur, 200, 6, changeTime);
    sellOld.setIdSecurityActionApp(7);
    buyNew.setIdSecurityActionApp(7);
    sellOld.setConnectedIdTransaction(buyNew.getIdTransaction());
    buyNew.setConnectedIdTransaction(sellOld.getIdTransaction());
    Transaction purchase = buy(DEPOT_A, oldStock, eur, 100, 10, at(2023, 1));
    Transaction sale = sell(DEPOT_A, newStock, eur, 200, 10, at(2025, 3));

    GermanTaxYearReport year2024 = run(2024, purchase, sellOld, buyNew, sale);
    GermanTaxYearReport year2025 = run(2025, purchase, sellOld, buyNew, sale);

    assertThat(year2024.sums).isEmpty();
    GermanTaxDetail detail = year2025.details.getFirst();
    assertThat(detail.costEur).isEqualTo(1_000.0, within(EXACT));
    assertThat(detail.lots.getFirst().idTransaction).isEqualTo(purchase.getIdTransaction());
    assertThat(detail.lots.getFirst().acquisitionTime).isEqualTo(purchase.getTransactionTime());
    assertThat(sum(year2025, GermanTaxKeys.BLOCK_SHARES, GermanTaxKeys.LINE_SHARE_GAINS).amountEur)
        .isEqualTo(1_000.0, within(EXACT));
  }

  @Test
  @DisplayName("The ISIN change of a fund may be a merger: the lots become incomplete and a later sale has no amount")
  void isinChangeOfAFund() {
    Security oldFund = fund("EUR", FundTaxClass.EQUITY_FUND);
    Security newFund = fund("EUR", FundTaxClass.EQUITY_FUND);
    Cashaccount eur = cashaccount("EUR");
    LocalDateTime changeTime = LocalDateTime.of(2025, 1, 15, 12, 0);
    Transaction sellOld = sell(DEPOT_A, oldFund, eur, 100, 100, changeTime);
    Transaction buyNew = buy(DEPOT_A, newFund, eur, 100, 100, changeTime);
    sellOld.setIdSecurityActionApp(7);
    buyNew.setIdSecurityActionApp(7);
    sellOld.setConnectedIdTransaction(buyNew.getIdTransaction());
    buyNew.setConnectedIdTransaction(sellOld.getIdTransaction());

    GermanTaxYearReport report = run(2025, buy(DEPOT_A, oldFund, eur, 100, 90, at(2024, 3)), sellOld, buyNew,
        sell(DEPOT_A, newFund, eur, 100, 120, at(2025, 6)));

    assertThat(sum(report, GermanTaxKeys.BLOCK_FUNDS, GermanTaxKeys.LINE_FUND_DISPOSAL, FundTaxClass.EQUITY_FUND)
        .complete).isFalse();
    assertThat(report.warnings).extracting(w -> w.code).contains(GermanTaxKeys.WARN_ISIN_CHANGE_FUND);
  }

  // ------------------------------------------------------------------------------------------ gaps

  @Test
  @DisplayName("A purchase without an exchange rate keeps its units, and the gap travels through a transfer and a sale")
  void gapTravelsWithTheLot() {
    Security stock = stock("USD");
    Cashaccount chf = cashaccount("CHF");
    market.rate("USD", LocalDate.of(2025, 6, 10), 0.9);
    LocalDateTime transferTime = LocalDateTime.of(2025, 3, 1, 12, 0);
    Transaction[] transfer = transfer(stock, chf, DEPOT_A, DEPOT_B, 50, 100, transferTime, 1);

    GermanTaxYearReport report = run(2025, buy(DEPOT_A, stock, chf, 100, 100, at(2025, 1)), transfer[0], transfer[1],
        sell(DEPOT_B, stock, chf, 50, 120, at(2025, 6)));

    GermanTaxSum gains = sum(report, GermanTaxKeys.BLOCK_SHARES, GermanTaxKeys.LINE_SHARE_GAINS);
    assertThat(gains.complete).isFalse();
    assertThat(gains.amountEur).isNull();
    assertThat(report.warnings).extracting(w -> w.code).contains(GermanTaxKeys.WARN_MISSING_RATE,
        GermanTaxKeys.WARN_SALE_FROM_INCOMPLETE_LOT);
    assertThat(report.details.getFirst().lots).extracting(lot -> lot.units).containsExactly(50.0);
  }

  @Test
  @DisplayName("A purchase at the price 0 without a system booking is no cost of 0")
  void purchaseWithoutConsideration() {
    Security stock = stock("EUR");
    Cashaccount eur = cashaccount("EUR");

    GermanTaxYearReport report = run(2025, buy(DEPOT_A, stock, eur, 100, 0, at(2025, 1)),
        sell(DEPOT_A, stock, eur, 100, 10, at(2025, 2)));

    assertThat(sum(report, GermanTaxKeys.BLOCK_SHARES, GermanTaxKeys.LINE_SHARE_GAINS).complete).isFalse();
    assertThat(report.warnings).extracting(w -> w.code).contains(GermanTaxKeys.WARN_PURCHASE_WITHOUT_CONSIDERATION);
  }

  @Test
  @DisplayName("A sale the purchases do not cover has no amount and raises no exception")
  void uncoveredSale() {
    Security stock = stock("EUR");
    Cashaccount eur = cashaccount("EUR");

    GermanTaxYearReport report = run(2025, buy(DEPOT_A, stock, eur, 50, 10, at(2025, 1)),
        sell(DEPOT_A, stock, eur, 100, 30, at(2025, 2)));

    assertThat(sum(report, GermanTaxKeys.BLOCK_SHARES, GermanTaxKeys.LINE_SHARE_GAINS).complete).isFalse();
    assertThat(report.warnings).extracting(w -> w.code).contains(GermanTaxKeys.WARN_UNCOVERED_SALE);
  }

  // ------------------------------------------------------------------------------------------ scope

  @Test
  @DisplayName("Purchases before 2009 (shares) and 2018 (funds) are legacy and in no form")
  void legacyLots() {
    Security stock = stock("EUR");
    Security fund = fund("EUR", FundTaxClass.EQUITY_FUND);
    Cashaccount eur = cashaccount("EUR");
    for (int year = 2018; year <= 2024; year++) {
      market.baseRate(year, 1.0);
      market.fundYear(fund, year, 100, 110);
    }

    GermanTaxYearReport report = run(2025, buy(DEPOT_A, stock, eur, 100, 10, at(2008, 5)),
        sell(DEPOT_A, stock, eur, 100, 30, at(2025, 2)), buy(DEPOT_A, fund, eur, 100, 10, at(2017, 5)),
        sell(DEPOT_A, fund, eur, 100, 30, at(2025, 3)));

    assertThat(sumOrNull(report, GermanTaxKeys.BLOCK_SHARES, GermanTaxKeys.LINE_SHARE_GAINS)).isNull();
    assertThat(sumOrNull(report, GermanTaxKeys.BLOCK_FUNDS, GermanTaxKeys.LINE_FUND_DISPOSAL,
        FundTaxClass.EQUITY_FUND)).isNull();
    assertThat(report.sums).filteredOn(s -> GermanTaxKeys.BLOCK_LEGACY.equals(s.block)).extracting(s -> s.category)
        .containsExactlyInAnyOrder(GermanTaxKeys.CATEGORY_SHARES, GermanTaxKeys.CATEGORY_FUNDS);
  }

  @Test
  @DisplayName("A fund without class, a certificate and a CFD are listed as unclassified and enter no sum")
  void unclassifiedInstruments() {
    Security fundWithoutClass = security("EUR", AssetclassType.EQUITIES, SpecialInvestmentInstruments.ETF);
    Security certificate = security("EUR", AssetclassType.COMMODITIES,
        SpecialInvestmentInstruments.ISSUER_RISK_PRODUCT);
    Security cfd = security("EUR", AssetclassType.EQUITIES, SpecialInvestmentInstruments.CFD);
    Cashaccount eur = cashaccount("EUR");
    Transaction open = buy(DEPOT_A, cfd, eur, 10, 100, at(2025, 1));
    open.setAssetInvestmentValue2(1.0);
    Transaction close = sell(DEPOT_A, cfd, eur, 10, 110, at(2025, 2));
    close.setAssetInvestmentValue2(1.0);
    close.setConnectedIdTransaction(open.getIdTransaction());
    close.setCashaccountAmount(100.0);

    GermanTaxYearReport report = run(2025, buy(DEPOT_A, fundWithoutClass, eur, 10, 10, at(2025, 1)),
        sell(DEPOT_A, fundWithoutClass, eur, 10, 12, at(2025, 2)), buy(DEPOT_A, certificate, eur, 10, 10, at(2025, 1)),
        sell(DEPOT_A, certificate, eur, 10, 12, at(2025, 2)), open, close);

    assertThat(report.sums).isEmpty();
    assertThat(report.unclassified).extracting(u -> u.reason).containsExactlyInAnyOrder(
        GermanTaxKeys.REASON_FUND_WITHOUT_CLASS, GermanTaxKeys.REASON_ISSUER_RISK_PRODUCT,
        GermanTaxKeys.REASON_MARGIN_INSTRUMENT);
    assertThat(report.unclassified).filteredOn(u -> u.amountKind.equals("SALE_PROCEEDS")).first()
        .satisfies(u -> assertThat(u.amountEur).isEqualTo(120.0, within(EXACT)));
  }

  @Test
  @DisplayName("A bond ETF is a fund of its class and no other capital claim")
  void bondEtfIsAFund() {
    Security bondEtf = security("EUR", AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.ETF);
    bondEtf.setFundTaxClass(FundTaxClass.OTHER_FUND);
    Cashaccount eur = cashaccount("EUR");

    GermanTaxYearReport report = run(2025, buy(DEPOT_A, bondEtf, eur, 10, 100, at(2025, 1)),
        sell(DEPOT_A, bondEtf, eur, 10, 110, at(2025, 2)));

    assertThat(sum(report, GermanTaxKeys.BLOCK_FUNDS, GermanTaxKeys.LINE_FUND_DISPOSAL, FundTaxClass.OTHER_FUND)
        .amountEur).isEqualTo(100.0, within(EXACT));
    assertThat(sumOrNull(report, GermanTaxKeys.BLOCK_OTHER_INCOME, GermanTaxKeys.LINE_CLAIM_GAINS)).isNull();
  }

  @Test
  @DisplayName("Sales of a later year do not change the report of an earlier tax year")
  void laterYearsDoNotMatter() {
    Security stock = stock("EUR");
    Cashaccount eur = cashaccount("EUR");
    Transaction buy = buy(DEPOT_A, stock, eur, 100, 10, at(2024, 1));
    Transaction sell2024 = sell(DEPOT_A, stock, eur, 50, 20, at(2024, 6));
    Transaction sell2025 = sell(DEPOT_A, stock, eur, 50, 40, at(2025, 6));

    GermanTaxYearReport report = run(2024, buy, sell2024, sell2025);

    assertThat(sum(report, GermanTaxKeys.BLOCK_SHARES, GermanTaxKeys.LINE_SHARE_GAINS).amountEur).isEqualTo(500.0,
        within(EXACT));
  }

  // ---------------------------------------------------------------------------------------------------------------

  private GermanTaxYearReport run(int taxYear, Transaction... transactions) {
    return new GermanTaxLotEngine(market, portfolios, Map.of(), TODAY).calculate(taxYear, List.of(transactions),
        List.of());
  }

  /** A run in which the application 1 is a spin-off with the given treatment and a German allocation of 20 %. */
  private GermanTaxYearReport runWithSpinOff(SpinOffTaxTreatmentDe treatment, int taxYear,
      Transaction... transactions) {
    return new GermanTaxLotEngine(market, portfolios, Map.of(), TODAY,
        Map.of(1, new GermanTaxSpinOff(treatment, 0.20))).calculate(taxYear, List.of(transactions), List.of());
  }

  /**
   * The three bookings of a spin-off of application 1 in depot A on 2 March 2026 at midnight, priced like the action
   * books them: the parent sale at the cum price 76, the repurchase at the ex-date close 64, the child at 48.
   */
  private Transaction[] spinOffLegs(Security parent, Security child, Cashaccount cashaccount, double parentUnits,
      double childUnits) {
    LocalDateTime exDate = LocalDateTime.of(2026, 3, 2, 0, 0);
    Transaction sale = sell(DEPOT_A, parent, cashaccount, parentUnits, 76, exDate);
    Transaction repurchase = buy(DEPOT_A, parent, cashaccount, parentUnits, 64, exDate);
    Transaction allotment = buy(DEPOT_A, child, cashaccount, childUnits, 48, exDate);
    sale.setSecurityActionRole(SecurityActionRole.SPIN_OFF_SALE);
    repurchase.setSecurityActionRole(SecurityActionRole.SPIN_OFF_REPURCHASE);
    allotment.setSecurityActionRole(SecurityActionRole.SPIN_OFF_CHILD);
    for (Transaction leg : List.of(sale, repurchase, allotment)) {
      leg.setIdSecurityActionApp(1);
    }
    return new Transaction[] { sale, repurchase, allotment };
  }

  private static GermanTaxSum sum(GermanTaxYearReport report, String block, String line) {
    return sum(report, block, line, null);
  }

  private static GermanTaxSum sum(GermanTaxYearReport report, String block, String line, FundTaxClass fundTaxClass) {
    final GermanTaxSum sum = sumOrNull(report, block, line, fundTaxClass);
    assertThat(sum).as("sum %s/%s", block, line).isNotNull();
    return sum;
  }

  private static GermanTaxSum sumOrNull(GermanTaxYearReport report, String block, String line) {
    return sumOrNull(report, block, line, null);
  }

  private static GermanTaxSum sumOrNull(GermanTaxYearReport report, String block, String line,
      FundTaxClass fundTaxClass) {
    return report.sums.stream().filter(s -> s.block.equals(block) && s.line.equals(line)
        && Objects.equals(s.fundTaxClass, fundTaxClass == null ? null : fundTaxClass.name())).findFirst().orElse(null);
  }

  private Security stock(String currency) {
    return security(currency, AssetclassType.EQUITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT);
  }

  private Security fund(String currency, FundTaxClass fundTaxClass) {
    final Security fund = security(currency, AssetclassType.EQUITIES, SpecialInvestmentInstruments.ETF);
    fund.setFundTaxClass(fundTaxClass);
    return fund;
  }

  private Security security(String currency, AssetclassType assetclassType, SpecialInvestmentInstruments instrument) {
    Assetclass assetclass = new Assetclass();
    assetclass.setCategoryType(assetclassType);
    assetclass.setSpecialInvestmentInstrument(instrument);
    Security security = new Security();
    security.setIdSecuritycurrency(nextSecurityId++);
    security.setName("Test " + security.getIdSecuritycurrency());
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
    return withDate(withId(nextId++, new Transaction(idSecurityaccount, cashaccount, security, units, quotation,
        TransactionType.ACCUMULATE, null, null, null, time, null)));
  }

  private Transaction sell(int idSecurityaccount, Security security, Cashaccount cashaccount, double units,
      double quotation, LocalDateTime time) {
    return withDate(withId(nextId++, new Transaction(idSecurityaccount, cashaccount, security, units, quotation,
        TransactionType.REDUCE, null, null, null, time, null)));
  }

  private Transaction dividend(int idSecurityaccount, Security security, Cashaccount cashaccount, double units,
      double perUnit, double taxCost, LocalDateTime time) {
    return withDate(withId(nextId++, new Transaction(idSecurityaccount, cashaccount, security, units, perUnit,
        TransactionType.DIVIDEND, taxCost, null, null, time, null)));
  }

  /** The sale and the purchase of a security account transfer, both with the id of the transfer. */
  private Transaction[] transfer(Security security, Cashaccount cashaccount, int from, int to, double units,
      double quotation, LocalDateTime time, int idTransfer) {
    Transaction sell = sell(from, security, cashaccount, units, quotation, time);
    Transaction buy = buy(to, security, cashaccount, units, quotation, time);
    sell.setIdSecurityTransfer(idTransfer);
    buy.setIdSecurityTransfer(idTransfer);
    return new Transaction[] { sell, buy };
  }

  private static Transaction costs(Transaction transaction, double transactionCost) {
    transaction.setTransactionCost(transactionCost);
    return transaction;
  }

  private static Transaction withId(Integer idTransaction, Transaction transaction) {
    transaction.setIdTransaction(idTransaction);
    return transaction;
  }

  /** The date column is filled when a transaction is persisted. */
  private static Transaction withDate(Transaction transaction) {
    ReflectionTestUtils.setField(transaction, "transactionDate", transaction.getTransactionTime().toLocalDate());
    return transaction;
  }

  /** Closing rates, fund prices, distributions and base interest rates as the test states them. */
  private static final class FakeMarket implements GermanTaxMarketData {
    private final Map<String, Map<LocalDate, Double>> rates = new HashMap<>();
    private final Map<String, Double> fixedRates = new HashMap<>();
    private final Map<Integer, Double> baseRates = new HashMap<>();
    private final Map<String, PricePoint> firstPrices = new HashMap<>();
    private final Map<String, PricePoint> lastPrices = new HashMap<>();
    private final Map<String, List<Distribution>> distributions = new HashMap<>();

    void rate(String currency, LocalDate day, double rate) {
      rates.computeIfAbsent(currency, _ -> new HashMap<>()).put(day, rate);
    }

    void baseRate(int year, double percent) {
      baseRates.put(year, percent);
    }

    void fundYear(Security fund, int year, double first, double last, Distribution... paid) {
      fundYearWithoutFirstPrice(fund, year, last);
      firstPrices.put(key(fund, year), new PricePoint(LocalDate.of(year, 1, 2), first));
      distributions.put(key(fund, year), new ArrayList<>(List.of(paid)));
    }

    void fundYearWithoutFirstPrice(Security fund, int year, double last) {
      lastPrices.put(key(fund, year), new PricePoint(LocalDate.of(year, 12, 30), last));
      distributions.put(key(fund, year), new ArrayList<>());
    }

    void fundYearWithUnknownDistributions(Security fund, int year, double first, double last) {
      fundYear(fund, year, first, last);
      distributions.remove(key(fund, year));
    }

    private static String key(Security fund, int year) {
      return fund.getIdSecuritycurrency() + "/" + year;
    }

    @Override
    public Double rateToEur(String currency, LocalDate date) {
      if ("EUR".equals(currency)) {
        return 1.0;
      }
      final Map<LocalDate, Double> dated = rates.get(currency);
      if (dated != null && dated.containsKey(date)) {
        return dated.get(date);
      }
      return fixedRates.get(currency);
    }

    @Override
    public PricePoint firstPrice(Integer idSecuritycurrency, int year) {
      return firstPrices.get(idSecuritycurrency + "/" + year);
    }

    @Override
    public PricePoint lastPrice(Integer idSecuritycurrency, int year) {
      return lastPrices.get(idSecuritycurrency + "/" + year);
    }

    @Override
    public List<Distribution> distributions(Security security, int year) {
      return distributions.get(key(security, year));
    }

    @Override
    public Double baseRate(int year) {
      return baseRates.get(year);
    }
  }

  /** All accounts belong to the portfolio 1 without domestic withholding unless a test assigns them otherwise. */
  private static final class FakePortfolios implements GermanTaxPortfolios {
    private final Map<Integer, Integer> portfolioOfAccount = new HashMap<>();
    private final Map<Integer, TaxWithholding> withholding = new HashMap<>();

    FakePortfolios() {
      withholding.put(1, TaxWithholding.NO_DOMESTIC_WITHHOLDING);
    }

    void assign(int idSecurityaccount, int idPortfolio, TaxWithholding taxWithholding) {
      portfolioOfAccount.put(idSecurityaccount, idPortfolio);
      withholding.put(idPortfolio, taxWithholding);
    }

    @Override
    public Integer portfolioOfSecurityaccount(Integer idSecurityaccount) {
      return portfolioOfAccount.getOrDefault(idSecurityaccount, 1);
    }

    @Override
    public Integer portfolioOfCashaccount(Integer idCashaccount) {
      return 1;
    }

    @Override
    public TaxWithholding withholding(Integer idPortfolio) {
      return withholding.get(idPortfolio);
    }

    @Override
    public List<PortfolioAssignment> assignments() {
      final List<PortfolioAssignment> assignments = new ArrayList<>();
      withholding.forEach((id, w) -> assignments.add(new PortfolioAssignment(id, "P" + id,
          w == null ? null : w.name(), true)));
      return assignments;
    }
  }

}
