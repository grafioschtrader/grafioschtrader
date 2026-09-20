package grafioschtrader.algo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.algo.strategy.model.AlgoStrategyImplementationType;
import grafioschtrader.algo.strategy.model.alerts.AbsoluteValuePriceAlert;
import grafioschtrader.algo.strategy.model.alerts.AlertConfigAdapter;
import grafioschtrader.algo.strategy.model.alerts.ExpressionAlert;
import grafioschtrader.algo.strategy.model.alerts.HoldingGainLosePercentAlert;
import grafioschtrader.algo.strategy.model.alerts.MaCrossingAlert;
import grafioschtrader.algo.strategy.model.alerts.PeriodPriceGainLosePercentAlert;
import grafioschtrader.algo.strategy.model.alerts.RsiThresholdAlert;
import grafioschtrader.entities.AlgoRuleStrategy.AlgoRuleStrategyParam;
import grafioschtrader.entities.AlgoStrategy;
import jakarta.validation.ValidationException;

/**
 * The adapter is what decides which of the two stored forms of an alert configuration is evaluated, so these tests are
 * about the one defect that made the whole alert feature inert: the edit dialog writes flat typed parameters and the
 * evaluation read only JSON.
 */
class AlertConfigAdapterTest {

  @Test
  void oldRebalancingParametersReceiveDefaultsAndNewValuesAreValidated() {
    var type = AlgoStrategyImplementationType.AS_HOLDING_TOP_REBALANCING;
    var model = grafioschtrader.algo.strategy.model.rebalacing.RebalancingTop.class;
    var old = AlertConfigAdapter.read(strategy(type, null, "timePeriodPerYear", "4", "thresholdPercentage", "5"),
        model);
    assertThat(old.getSecurityDeviationPercentage()).isEqualTo(5);
    assertThat(old.getMaxTradedSecuritiesPerAssetclass()).isEqualTo(3);
    var json = AlertConfigAdapter.read(strategy(type, "{\"timePeriodPerYear\":4,\"thresholdPercentage\":5}"), model);
    assertThat(json.getSecurityDeviationPercentage()).isEqualTo(5);
    var exact = AlertConfigAdapter.read(strategy(type, null, "timePeriodPerYear", "4", "thresholdPercentage", "5",
        "securityDeviationPercentage", "0", "maxTradedSecuritiesPerAssetclass", "1"), model);
    assertThat(exact.getSecurityDeviationPercentage()).isZero();
    assertThat(exact.getMaxTradedSecuritiesPerAssetclass()).isEqualTo(1);
    assertThatThrownBy(() -> AlertConfigAdapter.read(strategy(type, null, "timePeriodPerYear", "4",
        "thresholdPercentage", "5", "maxTradedSecuritiesPerAssetclass", "0"), model))
            .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> AlertConfigAdapter.read(strategy(type, null, "timePeriodPerYear", "4",
        "thresholdPercentage", "5", "securityDeviationPercentage", "101"), model))
            .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> AlertConfigAdapter.read(strategy(type, null, "timePeriodPerYear", "4",
        "thresholdPercentage", "5", "maxTradedSecuritiesPerAssetclass", "1.5"), model))
            .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> AlertConfigAdapter.read(
        strategy(type, "{\"timePeriodPerYear\":4,\"thresholdPercentage\":5,\"maxTradedSecuritiesPerAssetclass\":1.5}"),
        model)).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> AlertConfigAdapter.read(strategy(type, null, "timePeriodPerYear", "4",
        "thresholdPercentage", "5", "securityDeviationPercentage", "5garbage"), model))
            .isInstanceOf(ValidationException.class);
  }

  @Test
  @DisplayName("Flat parameters are read into every alert model the form can produce")
  void flatParametersBindToEveryAlertModel() {
    AbsoluteValuePriceAlert price = AlertConfigAdapter
        .read(strategy(AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_ABSOLUTE_PRICE, null, "lowerValue", "90.5",
            "upperValue", "120.25"), AbsoluteValuePriceAlert.class);
    assertThat(price.getLowerValue()).isEqualTo(90.5);
    assertThat(price.getUpperValue()).isEqualTo(120.25);

    HoldingGainLosePercentAlert holding = AlertConfigAdapter
        .read(strategy(AlgoStrategyImplementationType.AS_HOLDING_TOP_GAIN_LOSE, null, "gainPercentage", "25",
            "losePercentage", "10"), HoldingGainLosePercentAlert.class);
    assertThat(holding.getGainPercentage()).isEqualTo(25);
    assertThat(holding.getLosePercentage()).isEqualTo(10);

    PeriodPriceGainLosePercentAlert period = AlertConfigAdapter
        .read(strategy(AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_PERIOD_PRICE_GAIN_LOSE_PERCENT, null,
            "daysInPeriod", "30", "losePercentage", "15"), PeriodPriceGainLosePercentAlert.class);
    assertThat(period.getDaysInPeriod()).isEqualTo(30);
    assertThat(period.getLosePercentage()).isEqualTo(15);
    assertThat(period.getGainPercentage()).isNull();

    MaCrossingAlert ma = AlertConfigAdapter
        .read(strategy(AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_MA_CROSSING, null, "indicatorType", "EMA",
            "period", "50", "crossDirection", "ABOVE"), MaCrossingAlert.class);
    assertThat(ma.getIndicatorType()).isEqualTo("EMA");
    assertThat(ma.getPeriod()).isEqualTo(50);
    assertThat(ma.getCrossDirection()).isEqualTo("ABOVE");

    RsiThresholdAlert rsi = AlertConfigAdapter
        .read(strategy(AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_RSI_THRESHOLD, null, "rsiPeriod", "14",
            "lowerThreshold", "30"), RsiThresholdAlert.class);
    assertThat(rsi.getRsiPeriod()).isEqualTo(14);
    assertThat(rsi.getLowerThreshold()).isEqualTo(30);
    assertThat(rsi.getUpperThreshold()).isNull();

    ExpressionAlert expression = AlertConfigAdapter
        .read(strategy(AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_EXPRESSION, null, "expression",
            "price < SMA(200)"), ExpressionAlert.class);
    assertThat(expression.getExpression()).isEqualTo("price < SMA(200)");
  }

  @Test
  @DisplayName("A decimal is read the same whatever locale the server runs in")
  void decimalsAreLocaleIndependent() {
    Locale previous = Locale.getDefault();
    try {
      // A German or Swiss default locale reads the grouping separator as a point, so a converter inheriting the
      // default would turn the JSON number 120.5 into one thousand two hundred and five without any complaint.
      Locale.setDefault(Locale.GERMANY);
      AbsoluteValuePriceAlert price = AlertConfigAdapter.read(
          strategy(AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_ABSOLUTE_PRICE, null, "upperValue", "120.5"),
          AbsoluteValuePriceAlert.class);
      assertThat(price.getUpperValue()).isEqualTo(120.5);
    } finally {
      Locale.setDefault(previous);
    }
  }

  @Test
  @DisplayName("JSON is the fallback, and flat parameters win when a strategy carries both")
  void flatParametersTakePrecedenceOverJson() {
    AlgoStrategy jsonOnly = strategy(AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_ABSOLUTE_PRICE,
        "{\"lowerValue\":10.0,\"upperValue\":20.0}");
    assertThat(AlertConfigAdapter.read(jsonOnly, AbsoluteValuePriceAlert.class).getUpperValue()).isEqualTo(20.0);
    assertThat(AlertConfigAdapter.describeAmbiguity(jsonOnly)).isNull();

    AlgoStrategy both = strategy(AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_ABSOLUTE_PRICE,
        "{\"lowerValue\":10.0,\"upperValue\":20.0}", "upperValue", "99.0");
    assertThat(AlertConfigAdapter.read(both, AbsoluteValuePriceAlert.class).getUpperValue()).isEqualTo(99.0);
    assertThat(AlertConfigAdapter.describeAmbiguity(both)).contains("99.0").contains("flat parameters are evaluated");
  }

  @Test
  @DisplayName("A strategy with no configuration at all reads as null rather than as an empty alert")
  void noConfigurationReadsAsNull() {
    assertThat(
        AlertConfigAdapter.read(strategy(AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_ABSOLUTE_PRICE, null),
            AbsoluteValuePriceAlert.class)).isNull();
    assertThat(AlertConfigAdapter.read(
        strategy(AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_ABSOLUTE_PRICE, "", "upperValue", "   "),
        AbsoluteValuePriceAlert.class)).isNull();
  }

  @Test
  @DisplayName("Values the model cannot hold are reported instead of stored as a silently dead alert")
  void invalidValuesAreReported() {
    assertThatThrownBy(
        () -> AlertConfigAdapter.read(strategy(AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_MA_CROSSING, null,
            "indicatorType", "EMA", "period", "not a number", "crossDirection", "ABOVE"), MaCrossingAlert.class))
                .isInstanceOf(ValidationException.class).hasMessageContaining("period");

    // Out of the range the model declares: a moving average over 5000 days is not one the guard should let through.
    assertThatThrownBy(
        () -> AlertConfigAdapter.read(strategy(AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_MA_CROSSING, null,
            "indicatorType", "EMA", "period", "5000", "crossDirection", "ABOVE"), MaCrossingAlert.class))
                .isInstanceOf(ValidationException.class);

    assertThatThrownBy(() -> AlertConfigAdapter.read(
        strategy(AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_ABSOLUTE_PRICE, "{ not json at all"),
        AbsoluteValuePriceAlert.class)).isInstanceOf(ValidationException.class);
  }

  @Test
  @DisplayName("A parameter the model no longer has does not make the rest of the alert unreadable")
  void unknownParameterIsIgnored() {
    assertThatCode(() -> {
      AbsoluteValuePriceAlert price = AlertConfigAdapter
          .read(strategy(AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_ABSOLUTE_PRICE, null, "upperValue",
              "120.0", "retiredField", "whatever"), AbsoluteValuePriceAlert.class);
      assertThat(price.getUpperValue()).isEqualTo(120.0);
    }).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("The fingerprint changes exactly when the configuration does")
  void fingerprintTracksTheConfiguration() {
    AlgoStrategy original = strategy(AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_ABSOLUTE_PRICE, null,
        "upperValue", "120.0");
    AlgoStrategy same = strategy(AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_ABSOLUTE_PRICE, null, "upperValue",
        "120.0");
    AlgoStrategy edited = strategy(AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_ABSOLUTE_PRICE, null,
        "upperValue", "130.0");

    assertThat(AlertConfigAdapter.fingerprint(original)).isEqualTo(AlertConfigAdapter.fingerprint(same)).hasSize(64);
    assertThat(AlertConfigAdapter.fingerprint(edited)).isNotEqualTo(AlertConfigAdapter.fingerprint(original));
  }

  @Test
  @DisplayName("The model class is resolved from the strategy implementation type")
  void securityLevelModelClassIsResolved() {
    assertThat(AlertConfigAdapter
        .securityLevelModelClass(strategy(AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_RSI_THRESHOLD, null)))
            .isEqualTo(RsiThresholdAlert.class);
    // The mean reversion strategy is configured as JSON and has no security level model, so there is nothing to bind.
    assertThat(AlertConfigAdapter.securityLevelModelClass(
        strategy(AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_MEAN_REVERSION_DIP, null))).isNull();
  }

  /**
   * An AlgoStrategy carrying the given JSON and the given flat parameters.
   *
   * @param type      the implementation type
   * @param json      the strategyConfig, or null
   * @param flatPairs parameter name and value, alternating
   * @return the strategy
   */
  private static AlgoStrategy strategy(AlgoStrategyImplementationType type, String json, String... flatPairs) {
    AlgoStrategy strategy = new AlgoStrategy();
    strategy.setAlgoStrategyImplementations(type);
    strategy.setStrategyConfig(json);
    for (int i = 0; i < flatPairs.length; i += 2) {
      AlgoRuleStrategyParam param = new AlgoRuleStrategyParam();
      param.setParamValue(flatPairs[i + 1]);
      strategy.getAlgoRuleStrategyParamMap().put(flatPairs[i], param);
    }
    return strategy;
  }

}
