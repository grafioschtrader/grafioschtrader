package grafioschtrader.algo.strategy.model.complex;

import java.util.List;

import grafioschtrader.algo.strategy.model.complex.downside.IndicatorParams;

/**
 * Shared value checks of the executable configuration contract.
 *
 * <p>
 * The per-module validators express their contract in terms of these, so that a bound such as "a period is an integer
 * from 1 to 999" or "a fraction is greater than zero and at most one" reads and behaves the same wherever it is
 * applied. Every failure is an {@link IllegalArgumentException} whose message reaches the user through the translated
 * wrapper {@code algo.strategy.config.invalid}.
 * </p>
 */
final class ConfigChecks {

  private ConfigChecks() {
  }

  /**
   * Rejects the configuration with the given explanation unless the condition holds.
   *
   * @param condition what has to be true for the configuration to be executable
   * @param message   the explanation shown to the user, in English, as the argument of the translated wrapper
   */
  static void require(boolean condition, String message) {
    if (!condition)
      throw new IllegalArgumentException(message);
  }

  /**
   * Requires an indicator or statistical rule parameter block to name a comparison and a finite bound.
   *
   * @param p           the parameter block of the rule
   * @param statistical true for a statistical rule, which measures over {@code lookback} rather than {@code length}
   */
  static void params(IndicatorParams p, boolean statistical) {
    require(p != null && List.of("<", "<=", ">", ">=", "==", "!=").contains(String.valueOf(p.condition))
        && p.value != null && Double.isFinite(p.value), "Indicator comparison and finite value are required");
    period(statistical ? p.lookback : p.length);
  }

  /**
   * Requires an indicator period the available daily history can serve.
   *
   * @param value the number of observations the indicator measures over
   */
  static void period(Integer value) {
    require(value != null && value >= 1 && value <= 999, "Periods must be integers from 1 to 999");
  }

  /**
   * Requires a share of something, expressed as a fraction greater than zero and at most one.
   *
   * @param value the fraction, for example 0.10 for ten percent
   * @param field the configuration path named in the failure message
   */
  static void fraction(Double value, String field) {
    positive(value, field);
    require(value <= 1, field + " must be at most 1");
  }

  /**
   * Requires a finite amount greater than zero.
   *
   * @param value the amount
   * @param field the configuration path named in the failure message
   */
  static void positive(Double value, String field) {
    require(value != null && Double.isFinite(value) && value > 0, field + " must be positive and finite");
  }

  /**
   * Requires an adverse move, expressed as a fraction strictly between -1 and 0.
   *
   * @param value the fraction, for example -0.12 for a twelve percent drop
   * @param field the configuration path named in the failure message
   */
  static void negativeFraction(Double value, String field) {
    require(value != null && Double.isFinite(value) && value < 0 && value > -1, field + " must be between -1 and 0");
  }
}
