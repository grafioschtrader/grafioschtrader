package grafioschtrader.service;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.dto.TaxModelConfig;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.SecurityBondTerms;
import grafioschtrader.entities.SecuritySimulationMetadata;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.TradingPlatformPlan;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.securityaccount.DisposalCostDetail;
import grafioschtrader.reportviews.securityaccount.DisposalCostDetail.Part;
import grafioschtrader.reportviews.securityaccount.DisposalEstimate;
import grafioschtrader.reportviews.securityaccount.SecurityPositionCurrenyGroupSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionGrandSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.CouponDayCount;
import grafioschtrader.types.DistributionFrequency;
import grafioschtrader.types.SpecialInvestmentInstruments;
import grafioschtrader.types.TransactionType;

/**
 * Disposal cost estimate of the hypothetical sale from the fee model and simulation tax model YAML. Runs without Spring
 * and database: the estimators are the real ones, accounts and transactions are built in memory.
 */
@DisplayName("Disposal cost estimate of the hypothetical sale")
class DisposalCostEstimatorTest {

  private static final LocalDate DATE = LocalDate.of(2026, 9, 25);
  private static final int ID_SECURITY = 3;
  private static final int ID_BOND = 4;

  private static final String COMMISSION_10 = commission("Plan", "10");
  private static final String FX_HALF_PERCENT = "fx:\n  rules:\n    - name: Spread\n      condition: 'true'\n"
      + "      expression: \"0.5\"\n";

  /**
   * Swiss stamp duty: none with a foreign dealer, none for an exempt investor, otherwise 0.15 percent of the trade
   * value including accrued interest. Rule order matters: the dealer test comes first so that an unknown exemption only
   * matters with a Swiss dealer.
   */
  private static final String STAMP_DUTY = """
      version: 1
      transactionTaxes:
        rules:
          - name: foreign-dealer
            condition: 'dealerCountry != "CH"'
            expression: '0'
          - name: exempt
            condition: 'exemptInvestor == 1'
            expression: '0'
          - name: stamp-duty
            expression: 'tradeValue * 0.0015'
      """;

  private final TaxEvalExEstimator taxEstimator = new TaxEvalExEstimator();
  private final Map<Integer, Securityaccount> accounts = new HashMap<>();
  private final DisposalPositionCollector collector = new DisposalPositionCollector();
  private int idTransaction = 1;
  /** Total value lookup of the session; by default no daily total value exists. */
  private DisposalCostEstimator.TotalLookup totals = (_, _) -> null;

  private static String commission(String name, String expression) {
    return "rules:\n  - name: " + name + "\n    condition: 'true'\n    expression: '" + expression + "'\n";
  }

  private DisposalCostEstimator.Session session(String... taxModelYaml) {
    Map<String, TaxModelConfig> models = new TreeMap<>();
    for (String yaml : taxModelYaml) {
      models.put("CH", taxEstimator.parse(yaml));
    }
    return new DisposalCostEstimator.Session(DATE, collector, new TransactionCostEvalExEstimator(), taxEstimator,
        new FxMarkupEngine(), models, List.of(), accounts::get, (_, _, _) -> null, Map.of("CHF", 2, "USD", 2),
        totals);
  }

  private Securityaccount account(int id, String ownYaml, String planYaml, String dealerCountry, Boolean exempt) {
    var plan = new TradingPlatformPlan();
    plan.setIdTradingPlatformPlan(100 + id);
    plan.setFeeModelYaml(planYaml);
    plan.setCountryCode(dealerCountry);
    var account = new Securityaccount();
    account.setIdSecuritycashAccount(id);
    account.setName("Account " + id);
    account.setFeeModelYaml(ownYaml);
    account.setTradingPlatformPlan(plan);
    account.setTaxExemptInvestor(exempt);
    accounts.put(id, account);
    return account;
  }

  private static Security security(int id, String currency) {
    var security = new Security();
    security.setIdSecuritycurrency(id);
    security.setCurrency(currency);
    security.setAssetClass(
        new Assetclass(AssetclassType.EQUITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT, "Aktien", "Equities"));
    security.setLeverageFactor(1);
    return security;
  }

  private void buy(Security security, int idSecurityaccount, double units, LocalDate date, String cashCurrency) {
    trade(security, idSecurityaccount, units, date, cashCurrency, TransactionType.ACCUMULATE);
  }

  private void trade(Security security, int idSecurityaccount, double units, LocalDate date, String cashCurrency,
      TransactionType type) {
    var cashaccount = new Cashaccount();
    cashaccount.setCurrency(cashCurrency);
    var transaction = new Transaction();
    transaction.setIdTransaction(idTransaction++);
    transaction.setIdSecurityaccount(idSecurityaccount);
    transaction.setUnits(units);
    transaction.setTransactionTime(date.atStartOfDay());
    transaction.setTransactionType(type);
    transaction.setSecuritycurrency(security);
    transaction.setCashaccount(cashaccount);
    collector.accept(transaction, Map.of());
  }

  private static List<String> reasons(DisposalEstimate estimate, Part part) {
    return estimate.details().stream().filter(d -> d.part() == part && d.reason() != null)
        .map(DisposalCostDetail::reason).toList();
  }

  @Test
  @DisplayName("The commission of the account override wins over the plan, per account of a split position")
  void planModelVersusAccountOverride() {
    var security = security(ID_SECURITY, "CHF");
    account(1, null, COMMISSION_10, "CH", false);
    account(2, commission("Own", "units * 0.5"), COMMISSION_10, "CH", false);
    buy(security, 1, 60, DATE.minusYears(1), "CHF");
    buy(security, 2, 40, DATE.minusYears(1), "CHF");

    var estimate = session(STAMP_DUTY).estimateSell(security, 100, 50, null);

    // Account 1 sells 60 units under the plan (10), account 2 sells 40 units under its own model (40 * 0.5)
    assertThat(estimate.commission()).isEqualTo(30.0);
    assertThat(estimate.commissionComplete()).isTrue();
    assertThat(estimate.details()).filteredOn(d -> d.part() == Part.COMMISSION).extracting(DisposalCostDetail::rule)
        .containsExactlyInAnyOrder("Plan", "Own");
  }

  @Test
  @DisplayName("An account overriding only the commission still inherits the fx section of the plan")
  void fxInheritedIndependently() {
    var security = security(ID_SECURITY, "USD");
    account(1, commission("Own", "0"), COMMISSION_10 + FX_HALF_PERCENT, "US", false);
    buy(security, 1, 10, DATE.minusMonths(3), "CHF");

    var estimate = session(STAMP_DUTY).estimateSell(security, 10, 100, null);

    assertThat(estimate.commission()).isEqualTo(0.0);
    assertThat(estimate.fxComplete()).isTrue();
    assertThat(estimate.fxCost()).isEqualTo(5.0);
    assertThat(estimate.sameCurrencyNet()).isEqualTo(0.0);
    assertThat(estimate.complete()).isTrue();
  }

  @Test
  @DisplayName("No markup for a position settling in its own currency; its net proceeds stay for the transfer markup")
  void settlementInSecurityCurrencyHasNoMarkup() {
    var security = security(ID_SECURITY, "USD");
    account(1, null, COMMISSION_10, "US", false);
    buy(security, 1, 10, DATE.minusMonths(3), "USD");

    var estimate = session(STAMP_DUTY).estimateSell(security, 10, 100, null);

    assertThat(estimate.fxCost()).isEqualTo(0.0);
    assertThat(estimate.fxComplete()).isTrue();
    assertThat(estimate.sameCurrencyNet()).isEqualTo(1000 - 10.0);
  }

  @Test
  @DisplayName("Trade allowances count the booked trades of the account before the sale")
  void tradeAllowancesFromEarlierTrades() {
    var security = security(ID_SECURITY, "CHF");
    account(1, null, "rules:\n  - name: Free\n    condition: 'tradesInMonth < 2'\n    expression: '0'\n"
        + "  - name: Paid\n    condition: 'true'\n    expression: '10'\n", "CH", false);
    buy(security, 1, 10, DATE.minusMonths(2), "CHF");
    buy(security, 1, 10, DATE.withDayOfMonth(1), "CHF");

    assertThat(session(STAMP_DUTY).estimateSell(security, 20, 100, null).commission()).isEqualTo(0.0);

    trade(security, 1, 5, DATE.withDayOfMonth(2), "CHF", TransactionType.REDUCE);
    var estimate = session(STAMP_DUTY).estimateSell(security, 15, 100, null);
    assertThat(estimate.commission()).isEqualTo(10.0);
    assertThat(estimate.details()).filteredOn(d -> d.part() == Part.COMMISSION).extracting(DisposalCostDetail::rule)
        .containsExactly("Paid");
  }

  @Test
  @DisplayName("Swiss stamp duty depends on the dealer country and the exempt investor flag")
  void stampDutyWithDealerAndExemption() {
    var security = security(ID_SECURITY, "CHF");
    buy(security, 1, 10, DATE.minusYears(1), "CHF");

    account(1, null, COMMISSION_10, "CH", false);
    var swissNotExempt = session(STAMP_DUTY).estimateSell(security, 10, 100, null);
    assertThat(swissNotExempt.tax()).isEqualTo(1.5);
    assertThat(swissNotExempt.taxComplete()).isTrue();

    account(1, null, COMMISSION_10, "CH", true);
    var swissExempt = session(STAMP_DUTY).estimateSell(security, 10, 100, null);
    assertThat(swissExempt.tax()).isEqualTo(0.0);
    assertThat(swissExempt.taxComplete()).isTrue();

    account(1, null, COMMISSION_10, "CH", null);
    var swissUnknown = session(STAMP_DUTY).estimateSell(security, 10, 100, null);
    assertThat(swissUnknown.taxComplete()).isFalse();
    assertThat(reasons(swissUnknown, Part.TAX)).containsExactly("TAX_INPUT_MISSING");

    account(1, null, COMMISSION_10, "DE", null);
    var foreignUnknown = session(STAMP_DUTY).estimateSell(security, 10, 100, null);
    assertThat(foreignUnknown.tax()).isEqualTo(0.0);
    assertThat(foreignUnknown.taxComplete()).isTrue();
  }

  @Test
  @DisplayName("A direct bond is taxed on its clean value plus the accrued interest")
  void bondWithAccruedInterest() {
    var bond = security(ID_BOND, "CHF");
    bond.setAssetClass(new Assetclass(AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.DIRECT_INVESTMENT,
        "Anleihen", "Bonds"));
    bond.setActiveToDate(LocalDate.of(2030, 3, 31));
    bond.setDistributionFrequency(DistributionFrequency.DF_YEAR);
    var terms = new SecurityBondTerms();
    terms.setCouponRate(2.0);
    terms.setCouponDayCount(CouponDayCount.ACT_ACT_ICMA);
    var metadata = new SecuritySimulationMetadata();
    metadata.setBondTerms(terms);
    bond.setSimulationMetadata(metadata);
    account(1, null, COMMISSION_10, "CH", false);
    buy(bond, 1, 100, DATE.minusYears(1), "CHF");

    double accrued = new AlgoReplayCouponSchedule(AlgoReplayInputs.couponTerms(bond)).accrued(100, DATE);
    var estimate = session(STAMP_DUTY).estimateSell(bond, 100, 98, null);

    assertThat(accrued).isPositive();
    assertThat(estimate.tax()).isCloseTo((100 * 98 + accrued) * 0.0015, within(0.01));
  }

  @Test
  @DisplayName("Missing fee model, missing rule and missing tax models are incomplete, never zero and never an error")
  void missingModelsAreIncomplete() {
    var security = security(ID_SECURITY, "USD");
    account(1, null, null, "CH", false);
    buy(security, 1, 10, DATE.minusYears(1), "CHF");

    var noModels = session().estimateSell(security, 10, 100, null);
    assertThat(noModels.commission()).isNull();
    assertThat(noModels.tax()).isNull();
    assertThat(noModels.fxCost()).isNull();
    assertThat(noModels.complete()).isFalse();
    assertThat(reasons(noModels, Part.COMMISSION)).containsExactly(DisposalCostEstimator.FEE_MODEL_MISSING);
    assertThat(reasons(noModels, Part.TAX)).containsExactly(DisposalCostEstimator.TAX_NO_MODELS);
    assertThat(reasons(noModels, Part.FX)).containsExactly(DisposalCostEstimator.FX_MODEL_MISSING);

    account(1, null, "rules:\n  - name: Never\n    condition: 'false'\n    expression: '1'\n", "CH", false);
    var noRule = session(STAMP_DUTY).estimateSell(security, 10, 100, null);
    assertThat(noRule.commission()).isNull();
    assertThat(reasons(noRule, Part.COMMISSION)).containsExactly(DisposalCostEstimator.FEE_RULE_UNMATCHED);

    account(1, null, "rules: [ not valid", "CH", false);
    var invalid = session(STAMP_DUTY).estimateSell(security, 10, 100, null);
    assertThat(invalid.commissionComplete()).isFalse();
    assertThat(reasons(invalid, Part.COMMISSION)).containsExactly(DisposalCostEstimator.FEE_MODEL_INVALID);

    var unknownAccount = session(STAMP_DUTY).estimateSell(security(99, "CHF"), 10, 100, null);
    assertThat(unknownAccount.complete()).isFalse();
    assertThat(reasons(unknownAccount, Part.COMMISSION)).containsExactly(DisposalCostEstimator.ACCOUNT_UNKNOWN);
  }

  @Test
  @DisplayName("A tiered commission needing the account value is incomplete when that value is unknown")
  void fixedAssetsUnknownOnTheTransactionList() {
    var security = security(ID_SECURITY, "CHF");
    account(1, null, commission("Tier", "IF(fixedAssets > 100000, 5, 20)"), "CH", false);
    buy(security, 1, 10, DATE.minusYears(1), "CHF");

    var estimate = session(STAMP_DUTY).estimateSell(security, 10, 100, null);
    assertThat(estimate.commission()).isNull();
    assertThat(reasons(estimate, Part.COMMISSION)).containsExactly(DisposalCostEstimator.FIXED_ASSETS_UNKNOWN);

    var position = position(security, 10, 1000);
    session(STAMP_DUTY).estimatePositions(List.of(position), _ -> 1.0);
    assertThat(position.disposalTransactionCost).isEqualTo(20.0);
    assertThat(position.disposalComplete).isTrue();
  }

  @Test
  @DisplayName("A commission graded by the portfolio or tenant total uses the daily total value before the sale")
  void portfolioAndTenantTotalReachTheModel() {
    var security = security(ID_SECURITY, "CHF");
    var account = account(1, null,
        commission("Tier", "IF(portfolioTotal > 100000, 5, 20) + IF(tenantTotal > 500000, 1, 2)"), "CH", false);
    var portfolio = new Portfolio();
    portfolio.setIdPortfolio(11);
    account.setPortfolio(portfolio);
    account.setIdTenant(7);
    buy(security, 1, 10, DATE.minusYears(1), "CHF");
    totals = (idTenant, idPortfolio) -> idTenant == 7 ? (idPortfolio == null ? 600_000.0 : 150_000.0) : null;

    var estimate = session(STAMP_DUTY).estimateSell(security, 10, 100, null);
    assertThat(estimate.commission()).isEqualTo(6.0);
    assertThat(estimate.commissionComplete()).isTrue();
  }

  @Test
  @DisplayName("A commission graded by a total that has not been computed is incomplete, never graded against 0")
  void portfolioOrTenantTotalUnknown() {
    var security = security(ID_SECURITY, "CHF");
    var account = account(1, null, commission("Tier", "IF(portfolioTotal > 100000, 5, 20)"), "CH", false);
    var portfolio = new Portfolio();
    portfolio.setIdPortfolio(11);
    account.setPortfolio(portfolio);
    account.setIdTenant(7);
    buy(security, 1, 10, DATE.minusYears(1), "CHF");

    var portfolioUnknown = session(STAMP_DUTY).estimateSell(security, 10, 100, null);
    assertThat(portfolioUnknown.commission()).isNull();
    assertThat(reasons(portfolioUnknown, Part.COMMISSION))
        .containsExactly(DisposalCostEstimator.PORTFOLIO_TOTAL_UNKNOWN);

    account(1, null, commission("Tier", "IF(tenantTotal > 100000, 5, 20)"), "CH", false).setIdTenant(7);
    totals = (_, idPortfolio) -> idPortfolio == null ? null : 150_000.0;
    var tenantUnknown = session(STAMP_DUTY).estimateSell(security, 10, 100, null);
    assertThat(tenantUnknown.commission()).isNull();
    assertThat(reasons(tenantUnknown, Part.COMMISSION)).containsExactly(DisposalCostEstimator.TENANT_TOTAL_UNKNOWN);
  }

  @Test
  @DisplayName("Positions, group and grand total carry the disposal costs and are flagged when one is incomplete")
  void totalsFlaggedIncomplete() {
    var complete = security(ID_SECURITY, "CHF");
    var incomplete = security(5, "CHF");
    account(1, null, COMMISSION_10, "CH", false);
    account(2, null, null, "CH", false);
    buy(complete, 1, 10, DATE.minusYears(1), "CHF");
    buy(incomplete, 2, 10, DATE.minusYears(1), "CHF");
    var positionComplete = position(complete, 10, 1000);
    var positionIncomplete = position(incomplete, 10, 500);
    var closed = position(security(6, "CHF"), 0, 0);

    session(STAMP_DUTY).estimatePositions(List.of(positionComplete, positionIncomplete, closed), _ -> 1.0);
    var group = new SecurityPositionCurrenyGroupSummary("CHF", 1.0, 2);
    for (var position : List.of(positionComplete, positionIncomplete, closed)) {
      position.calcMainCurrency(1.0);
      group.addToGroupSummaryAndCalcGroupTotals(position);
    }
    var grand = new SecurityPositionGrandSummary("CHF", 2);
    grand.calcGrandTotal(group);

    assertThat(positionComplete.disposalComplete).isTrue();
    assertThat(positionComplete.getDisposalCostMC()).isEqualTo(11.5);
    assertThat(positionComplete.getValueAfterDisposalMC()).isEqualTo(988.5);
    assertThat(positionIncomplete.disposalComplete).isFalse();
    assertThat(positionIncomplete.disposalTransactionCost).isNull();
    assertThat(positionIncomplete.getDisposalCostMC()).isEqualTo(0.75);
    assertThat(closed.disposalComplete).isNull();
    assertThat(group.getGroupDisposalCostMC()).isEqualTo(12.25);
    assertThat(group.groupDisposalComplete).isFalse();
    assertThat(grand.getGrandDisposalCostMC()).isEqualTo(12.25);
    assertThat(grand.getGrandValueAfterDisposalMC()).isEqualTo(1500 - 12.25);
    assertThat(grand.grandDisposalComplete).isFalse();
  }

  private static SecurityPositionSummary position(Security security, double units, double value) {
    var position = new SecurityPositionSummary("CHF", security, Map.of("CHF", 2));
    position.units = units;
    position.valueSecurity = value;
    return position;
  }
}
