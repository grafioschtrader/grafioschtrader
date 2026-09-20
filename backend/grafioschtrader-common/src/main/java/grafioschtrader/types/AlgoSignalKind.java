package grafioschtrader.types;

import java.util.Arrays;

/**
 * Kind of signal an algo evaluation produced. It is one component of the identity that daily deduplication works on, so
 * two different kinds fired on the same day for the same instrument are two alarms rather than one.
 *
 * <p>
 * The values 1 to 6 are the ones the {@code algo_message_alert.alarm_type} column has always documented. Only the alert
 * kinds are produced today; the trading kinds are reserved for the strategy signals that later strategy modules add
 * through the same notification path, and their numbers must not be reused for anything else.
 * </p>
 */
public enum AlgoSignalKind {

  /** A configured bound on the instrument price was crossed. */
  PRICE_ALERT((byte) 1),
  /** An entry condition of a trading strategy was met. Reserved, not produced yet. */
  ENTRY_SIGNAL((byte) 2),
  /** A profit taking tranche became executable. Reserved, not produced yet. */
  PROFIT_TAKE((byte) 3),
  /** A stop loss or other downside exit condition was met. Reserved, not produced yet. */
  STOP_LOSS((byte) 4),
  /** An allocation drifted beyond its rebalancing tolerance. Reserved, not produced yet. */
  REBALANCE_DRIFT((byte) 5),
  /** A risk control was breached. Reserved, not produced yet. */
  RISK_BREACH((byte) 6),
  /** The gain or loss of an actual holding passed a configured threshold. */
  HOLDING_GAIN_LOSS((byte) 7),
  /** The price change over a configured lookback period passed a threshold. */
  PERIOD_PRICE_CHANGE((byte) 8),
  /** The price crossed its moving average. */
  MA_CROSSING((byte) 9),
  /** The RSI crossed a configured threshold. */
  RSI_THRESHOLD((byte) 10),
  /** A user supplied expression evaluated to true. */
  EXPRESSION((byte) 11);

  private final Byte value;

  private AlgoSignalKind(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static AlgoSignalKind getAlgoSignalKindByValue(byte value) {
    return Arrays.stream(AlgoSignalKind.values()).filter(e -> e.getValue().equals(value)).findFirst().orElse(null);
  }

}
