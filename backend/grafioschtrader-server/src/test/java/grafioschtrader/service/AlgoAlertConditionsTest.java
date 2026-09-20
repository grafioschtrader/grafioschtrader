package grafioschtrader.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import grafioschtrader.algo.strategy.model.AlgoStrategyImplementationType;
import grafioschtrader.algo.strategy.model.alerts.AlertConfigAdapter;
import grafioschtrader.entities.*;
import grafioschtrader.evalex.AlertExpressionSupport;
import grafioschtrader.types.AlgoSignalKind;

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

  @Test
  void holdingPriceAndPercentageConditionsHaveSeparateSignals() throws Exception {
    var evaluator = new AlgoAlarmEvaluationService();
    var holdings = mock(AlgoHoldingGainLossService.class);
    var crossings = mock(AlgoAlertStateService.class);
    var recorder = mock(AlgoAlarmRecorder.class);
    ReflectionTestUtils.setField(evaluator, "algoHoldingGainLossService", holdings);
    ReflectionTestUtils.setField(evaluator, "algoAlertStateService", crossings);
    ReflectionTestUtils.setField(evaluator, "algoAlarmRecorder", recorder);
    Security security = new Security();
    security.setIdSecuritycurrency(4);
    security.setSLast(110d);
    AlgoStrategy strategy = new AlgoStrategy();
    strategy.setIdAlgoRuleStrategy(3);
    strategy.setAlgoStrategyImplementations(AlgoStrategyImplementationType.AS_HOLDING_TOP_GAIN_LOSE);
    strategy.setStrategyConfig("{\"upperValue\":100,\"gainPercentage\":5}");
    var scope = new AlgoAlertScope(42, strategy, security, "Holding", true);
    when(holdings.observe(42, security, 110d)).thenReturn(new AlgoHoldingGainLossService.HoldingObservation(true, 10d));
    when(crossings.observe(anyInt(), anyInt(), anyInt(), anyString(), anyString(), anyDouble(), anyDouble()))
        .thenReturn(AlgoCrossingResult.CROSSED_UP);
    evaluator.evaluateOne(scope, security, strategy.getAlgoStrategyImplementations(), LocalDate.now());
    verify(recorder).record(eq(scope), eq(AlgoSignalKind.PRICE_ALERT), eq((byte) 1), anyString(), any());
    verify(recorder).record(eq(scope), eq(AlgoSignalKind.HOLDING_GAIN_LOSS), eq((byte) 1), anyString(), any());
    reset(recorder);
    when(holdings.observe(42, security, 110d))
        .thenReturn(new AlgoHoldingGainLossService.HoldingObservation(false, null));
    assertThatThrownBy(
        () -> evaluator.evaluateOne(scope, security, strategy.getAlgoStrategyImplementations(), LocalDate.now()))
            .hasMessageContaining("no open position");
    verify(crossings).discard(3, 4);
    verifyNoInteractions(recorder);
  }
}
