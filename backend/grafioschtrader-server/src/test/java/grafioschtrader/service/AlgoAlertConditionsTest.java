package grafioschtrader.service;

import static org.assertj.core.api.Assertions.*;

import java.time.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import grafioschtrader.algo.strategy.model.AlgoStrategyImplementationType;
import grafioschtrader.algo.strategy.model.alerts.AlertConfigAdapter;
import grafioschtrader.entities.*;
import grafioschtrader.evalex.AlertExpressionSupport;

/** Behavioral regressions for configuration accepted by REST and evaluated by the alert service. */
class AlgoAlertConditionsTest {
  @ParameterizedTest
  @ValueSource(strings = { "price < SMA(200)", "price < sma (200) && RSI(14) < 30", "EMA(50) > EMA(200)",
      "price - prevClose", "IF(price > 5, 1, 0)", "TRUE" })
  void supportedExpressionsParseWithoutData(String text) {
    assertThatCode(() -> AlertExpressionSupport.validate(text)).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = { "", "price <", "unknown > 0", "price > 0 OR unknown > 0", "UNKNOWN(4)", "SMA(0)", "SMA(1.5)",
      "RSI(1000)", "SMA(price)", "\"text\"", "IF(price > 2, 1, \"bad\")" })
  void invalidExpressionsFailBeforeEvaluation(String text) {
    assertThatThrownBy(() -> AlertExpressionSupport.validate(text)).isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = { "{}", "{\"lowerValue\":10,\"upperValue\":10}", "{\"lowerValue\":20,\"upperValue\":10}" })
  void invalidPriceBoundsRejected(String json) {
    AlgoStrategy strategy = new AlgoStrategy();
    strategy.setAlgoStrategyImplementations(AlgoStrategyImplementationType.AS_OBSERVED_SECURITY_ABSOLUTE_PRICE);
    strategy.setStrategyConfig(json);
    assertThatThrownBy(() -> AlertConfigAdapter.read(strategy))
        .isInstanceOf(jakarta.validation.ValidationException.class);
  }

}
