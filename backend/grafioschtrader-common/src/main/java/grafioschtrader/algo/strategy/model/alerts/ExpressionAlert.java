package grafioschtrader.algo.strategy.model.alerts;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Configuration for a custom EvalEx expression alert (security level only). The expression is evaluated using the
 * <a href="https://github.com/uklimaschewski/EvalEx">EvalEx</a> library. The alert fires when the expression
 * evaluates to a truthy result (boolean {@code true} or non-zero numeric value).
 *
 * <p><b>Intraday price variables</b> (always available):</p>
 * <ul>
 *   <li>{@code price} - last traded price ({@code sLast})</li>
 *   <li>{@code prevClose} - previous closing price ({@code sPrevClose})</li>
 *   <li>{@code volume} - last traded volume ({@code sVolume})</li>
 *   <li>{@code open} - opening price ({@code sOpen})</li>
 *   <li>{@code high} - day high ({@code sHigh})</li>
 *   <li>{@code low} - day low ({@code sLow})</li>
 * </ul>
 *
 * <p><b>Indicator functions</b> (computed from historical closing prices, loaded on demand):</p>
 * <ul>
 *   <li>{@code SMA(period)} - Simple Moving Average, e.g. {@code SMA(200)}</li>
 *   <li>{@code EMA(period)} - Exponential Moving Average, e.g. {@code EMA(50)}</li>
 *   <li>{@code RSI(period)} - Relative Strength Index (0-100), e.g. {@code RSI(14)}</li>
 * </ul>
 *
 * <p><b>Expression examples:</b></p>
 * <ul>
 *   <li>{@code "price < SMA(200)"} - Price below 200-day SMA</li>
 *   <li>{@code "price < SMA(200) AND RSI(14) < 30"} - Price below SMA and RSI oversold</li>
 *   <li>{@code "EMA(50) > EMA(200)"} - Golden cross (short-term EMA above long-term)</li>
 *   <li>{@code "price > 100"} - Simple price threshold (no history loaded)</li>
 * </ul>
 *
 * <p>Evaluated by Tier 2 (scheduled indicator evaluation).</p>
 */
public class ExpressionAlert {

  /**
   * EvalEx expression string evaluated against the security's intraday price fields and optional indicator functions.
   * The expression may resolve to a boolean or numeric value; boolean {@code true} or a non-zero numeric result
   * triggers the alert. Maximum 500 characters.
   */
  @NotNull
  @Size(max = 500)
  String expression;

  public String getExpression() {
    return expression;
  }

  public void setExpression(String expression) {
    this.expression = expression;
  }
}
