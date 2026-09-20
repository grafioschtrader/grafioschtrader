package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.entities.TradingPlatformPlan;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;
import grafioschtrader.types.TransactionType;

/**
 * The fee a historical replay charges comes from the security account the fill settles in, so what matters here is
 * which model wins, what an environment without one costs, and that a model which cannot answer stops the order instead
 * of making it free.
 */
@DisplayName("Transaction costs of a historical replay")
class AlgoReplayFeesTest {

  private static final LocalDate FILL = LocalDate.of(2024, 3, 14);

  private static final String FLAT_TEN = """
      rules:
        - name: "Flat"
          condition: "true"
          expression: "10.0"
      """;

  private static final String FLAT_TWENTY = """
      rules:
        - name: "Flat"
          condition: "true"
          expression: "20.0"
      """;

  private static final String NOTHING_MATCHES = """
      rules:
        - name: "Never"
          condition: "units < 0"
          expression: "1.0"
      """;

  private static final String ONE_PERCENT_MIN_NINE = """
      rules:
        - name: "Tiered"
          condition: "true"
          expression: "MAX(9.0, tradeValue * 0.01)"
      """;

  private static final String ONLY_2025 = """
      periods:
        - validFrom: "2025-01-01"
          rules:
            - name: "Flat"
              condition: "true"
              expression: "5.0"
      """;

  private static final String BUY_OR_SELL = """
      rules:
        - name: "Buy"
          condition: "tradeDirection == 0"
          expression: "10.0"
        - name: "Sell"
          condition: "true"
          expression: "7.0"
      """;

  private static final String BY_ACCOUNT_SIZE = """
      rules:
        - name: "Large"
          condition: "fixedAssets > 100000"
          expression: "5.0"
        - name: "Small"
          condition: "true"
          expression: "25.0"
      """;

  private final TransactionCostEvalExEstimator estimator = new TransactionCostEvalExEstimator();

  private Security security() {
    Assetclass assetclass = new Assetclass();
    assetclass.setSpecialInvestmentInstrument(SpecialInvestmentInstruments.DIRECT_INVESTMENT);
    assetclass.setCategoryType(AssetclassType.EQUITIES);
    Stockexchange stockexchange = new Stockexchange();
    stockexchange.setMic("XSWX");
    Security security = new Security();
    security.setAssetClass(assetclass);
    security.setStockexchange(stockexchange);
    security.setCurrency("CHF");
    return security;
  }

  private Securityaccount account(Integer id, String planYaml, String accountYaml) {
    TradingPlatformPlan plan = new TradingPlatformPlan();
    plan.setFeeModelYaml(planYaml);
    Securityaccount sa = new Securityaccount();
    sa.setIdSecuritycashAccount(id);
    sa.setTradingPlatformPlan(plan);
    sa.setFeeModelYaml(accountYaml);
    return sa;
  }

  private double cost(Securityaccount sa, double units, double quotation) {
    return new AlgoReplayFees(List.of(sa), estimator).cost(sa.getId(), security(), units, quotation,
        TransactionType.ACCUMULATE, FILL, 0);
  }

  @Test
  @DisplayName("An account without any model keeps the run free of charge")
  void noModelCostsNothing() {
    Securityaccount sa = account(1, null, null);
    assertThat(AlgoReplayFees.effectiveYaml(sa)).isNull();
    assertThat(AlgoReplayFees.anyModelActive(List.of(sa))).isFalse();
    assertThat(cost(sa, 10, 100)).isZero();
  }

  @Test
  @DisplayName("A blank model counts as none rather than as one that cannot answer")
  void blankModelCostsNothing() {
    Securityaccount sa = account(1, "   ", "");
    assertThat(AlgoReplayFees.anyModelActive(List.of(sa))).isFalse();
    assertThat(cost(sa, 10, 100)).isZero();
  }

  @Test
  @DisplayName("The model of the trading platform plan applies when the account overrides nothing")
  void planModelApplies() {
    Securityaccount sa = account(1, FLAT_TEN, null);
    assertThat(AlgoReplayFees.anyModelActive(List.of(sa))).isTrue();
    assertThat(cost(sa, 10, 100)).isEqualTo(10.0);
  }

  @Test
  @DisplayName("An account level model overrides the one of its trading platform plan")
  void accountOverrideWins() {
    assertThat(cost(account(1, FLAT_TEN, FLAT_TWENTY), 10, 100)).isEqualTo(20.0);
  }

  @Test
  @DisplayName("Only the model of the account the order settles in is charged")
  void feeIsPerAccount() {
    Securityaccount free = account(1, null, null);
    Securityaccount charging = account(2, FLAT_TEN, null);
    AlgoReplayFees fees = new AlgoReplayFees(List.of(free, charging), estimator);
    assertThat(fees.cost(1, security(), 10, 100, TransactionType.ACCUMULATE, FILL, 0)).isZero();
    assertThat(fees.cost(2, security(), 10, 100, TransactionType.ACCUMULATE, FILL, 0)).isEqualTo(10.0);
    assertThat(AlgoReplayFees.anyModelActive(List.of(free, charging)))
        .as("one charging account is enough for the run to state that it charges").isTrue();
  }

  @Test
  @DisplayName("A percentage with a floor follows the size of the order and is rounded to the cent")
  void percentageFollowsTheOrder() {
    Securityaccount sa = account(1, ONE_PERCENT_MIN_NINE, null);
    assertThat(cost(sa, 100, 100)).as("one percent of 10000").isEqualTo(100.0);
    assertThat(cost(sa, 1, 100)).as("the floor of the model, not one percent of 100").isEqualTo(9.0);
    assertThat(cost(sa, 3, 1111.11)).as("one percent of 3333.33, rounded the way money is").isEqualTo(33.33);
  }

  @Test
  @DisplayName("A sale is charged too, and it is charged as a sale")
  void saleIsCharged() {
    Securityaccount sa = account(1, BUY_OR_SELL, null);
    AlgoReplayFees fees = new AlgoReplayFees(List.of(sa), estimator);
    assertThat(fees.cost(1, security(), 10, 100, TransactionType.ACCUMULATE, FILL, 0)).isEqualTo(10.0);
    assertThat(fees.cost(1, security(), 10, 100, TransactionType.REDUCE, FILL, 0)).isEqualTo(7.0);
  }

  @Test
  @DisplayName("The sign of the units never reaches the model; a reduction is sized like a purchase")
  void unitsAreAbsolute() {
    Securityaccount sa = account(1, ONE_PERCENT_MIN_NINE, null);
    AlgoReplayFees fees = new AlgoReplayFees(List.of(sa), estimator);
    assertThat(fees.cost(1, security(), -100, 100, TransactionType.REDUCE, FILL, 0)).isEqualTo(100.0);
  }

  @Test
  @DisplayName("A model no rule of which matches stops the order instead of making it free")
  void unmatchedModelFails() {
    Securityaccount sa = account(1, NOTHING_MATCHES, null);
    assertThatThrownBy(() -> cost(sa, 10, 100)).isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("REPLAY_FEE_MODEL_FAILED");
  }

  @Test
  @DisplayName("A period model that does not reach back over the replayed day stops the order")
  void modelWithoutAPeriodForTheDayFails() {
    Securityaccount sa = account(1, ONLY_2025, null);
    assertThatThrownBy(() -> cost(sa, 10, 100)).isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("REPLAY_FEE_MODEL_FAILED");
  }

  @Test
  @DisplayName("A model graded by the size of the account is given that size")
  void fixedAssetsReachTheModel() {
    Securityaccount sa = account(1, BY_ACCOUNT_SIZE, null);
    AlgoReplayFees fees = new AlgoReplayFees(List.of(sa), estimator);
    assertThat(fees.cost(1, security(), 10, 100, TransactionType.ACCUMULATE, FILL, 1_000_000)).isEqualTo(5.0);
    assertThat(fees.cost(1, security(), 10, 100, TransactionType.ACCUMULATE, FILL, 50_000)).isEqualTo(25.0);
  }
}
