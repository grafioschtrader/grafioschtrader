package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The rule that turns a level into a crossing.
 *
 * <p>
 * It is the difference between an alert that reports what happened and one that reports what the user configured. A
 * level test says "the price is above 120" and repeats itself for as long as that stays true; the crossing rule says
 * "the price has just gone above 120" and says it once.
 * </p>
 */
class AlgoCrossingRuleTest {

  private static final boolean SAME_CONFIG = true;

  @Test
  @DisplayName("Which side of the bound a value is on")
  void sideOfTheBound() {
    assertThat(AlgoAlertStateService.sideOf(99.9, 100.0)).isEqualTo((byte) -1);
    assertThat(AlgoAlertStateService.sideOf(100.0, 100.0)).isEqualTo((byte) 0);
    assertThat(AlgoAlertStateService.sideOf(100.1, 100.0)).isEqualTo((byte) 1);
  }

  @Test
  @DisplayName("Below, above, still above yields exactly one upward crossing")
  void oneCrossingPerTransition() {
    assertThat(replay(-1, 1, 1)).containsExactly(AlgoCrossingResult.CROSSED_UP, AlgoCrossingResult.NO_CHANGE);
  }

  @Test
  @DisplayName("A value that is already past the bound reports nothing until it comes back and crosses again")
  void alreadyPastTheBoundIsNotACrossing() {
    // This is the case the level test got wrong: an alert created while the price is above its upper bound used to
    // fire on the first evaluation and on every one after.
    assertThat(replay(1, 1, 1)).containsExactly(AlgoCrossingResult.NO_CHANGE, AlgoCrossingResult.NO_CHANGE);
    assertThat(replay(1, -1, 1)).containsExactly(AlgoCrossingResult.CROSSED_DOWN, AlgoCrossingResult.CROSSED_UP);
  }

  @Test
  @DisplayName("Touching the bound exactly is not yet a crossing of it")
  void touchingTheBoundIsNotACrossing() {
    assertThat(replay(-1, 0, 1)).containsExactly(AlgoCrossingResult.NO_CHANGE, AlgoCrossingResult.CROSSED_UP);
    assertThat(replay(1, 0, -1)).containsExactly(AlgoCrossingResult.NO_CHANGE, AlgoCrossingResult.CROSSED_DOWN);
  }

  @Test
  @DisplayName("A changed configuration re-establishes the baseline instead of reporting a crossing of the old bound")
  void aChangedConfigurationRebaselines() {
    assertThat(AlgoAlertStateService.classify((byte) -1, (byte) 1, false))
        .isEqualTo(AlgoCrossingResult.BASELINE_ESTABLISHED);
    assertThat(AlgoAlertStateService.classify((byte) -1, (byte) 1, SAME_CONFIG))
        .isEqualTo(AlgoCrossingResult.CROSSED_UP);
  }

  @Test
  @DisplayName("Only a crossing carries a direction, and the two directions are separate signals")
  void directionIsCarriedOnlyByACrossing() {
    assertThat(AlgoCrossingResult.CROSSED_UP.isCrossing()).isTrue();
    assertThat(AlgoCrossingResult.CROSSED_DOWN.isCrossing()).isTrue();
    assertThat(AlgoCrossingResult.BASELINE_ESTABLISHED.isCrossing()).isFalse();
    assertThat(AlgoCrossingResult.NO_CHANGE.isCrossing()).isFalse();

    assertThat(AlgoCrossingResult.CROSSED_UP.direction()).isEqualTo((byte) 1);
    assertThat(AlgoCrossingResult.CROSSED_DOWN.direction()).isEqualTo((byte) -1);
    assertThat(AlgoCrossingResult.BASELINE_ESTABLISHED.direction()).isZero();
    assertThat(AlgoCrossingResult.NO_CHANGE.direction()).isZero();
  }

  @Test
  @DisplayName("An expression is recognised as using indicators whatever case or spacing it is written in")
  void indicatorFunctionsAreRecognised() {
    assertThat(AlgoAlarmEvaluationService.usesIndicatorFunctions("price < SMA(200)")).isTrue();
    // EvalEx accepts a function name in any case and tolerates whitespace before the parenthesis. The literal search
    // for "SMA(" missed both, and the expression was then built without the function and failed on an unknown name.
    assertThat(AlgoAlarmEvaluationService.usesIndicatorFunctions("price < sma (200)")).isTrue();
    assertThat(AlgoAlarmEvaluationService.usesIndicatorFunctions("EMA(50) > EMA(200)")).isTrue();
    assertThat(AlgoAlarmEvaluationService.usesIndicatorFunctions("rsi(14) < 30")).isTrue();
    assertThat(AlgoAlarmEvaluationService.usesIndicatorFunctions("price > 100")).isFalse();
    assertThat(AlgoAlarmEvaluationService.usesIndicatorFunctions("prevClose > 100")).isFalse();
    assertThat(AlgoAlarmEvaluationService.usesIndicatorFunctions(null)).isFalse();
  }

  /**
   * Replays a sequence of observed sides, starting from a baseline established by the first one.
   *
   * @param sides the sides observed, in order; the first establishes the baseline
   * @return the classification of every observation after the first
   */
  private static List<AlgoCrossingResult> replay(int... sides) {
    List<AlgoCrossingResult> results = new ArrayList<>();
    for (int i = 1; i < sides.length; i++) {
      results.add(AlgoAlertStateService.classify((byte) sides[i - 1], (byte) sides[i], SAME_CONFIG));
    }
    return results;
  }

}
