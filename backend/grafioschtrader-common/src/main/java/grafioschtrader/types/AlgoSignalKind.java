package grafioschtrader.types;

import java.util.Arrays;

/**
 * Kind of signal an algo evaluation produced. It is one component of the identity that daily deduplication works on, so
 * two different kinds fired on the same day for the same instrument are two alarms rather than one.
 *
 * <p>
 * Every constant is produced, either by the alert evaluation or by a strategy module. The numbers are stored in
 * {@code algo_message_alert.alarm_type}, so none of them may be reused for anything else.
 * </p>
 */
public enum AlgoSignalKind {

  /** A configured bound on the instrument price was crossed. */
  PRICE_ALERT((byte) 1),
  /** An entry or add-on condition of a trading strategy was met; produced by AlgoMeanReversionEvaluationService. */
  ENTRY_SIGNAL((byte) 2),
  /** A profit taking tranche or exit became executable; produced by AlgoMeanReversionEvaluationService. */
  PROFIT_TAKE((byte) 3),
  /** A stop loss or other downside exit condition was met; produced by AlgoMeanReversionEvaluationService. */
  STOP_LOSS((byte) 4),
  /**
   * A rebalancing checkpoint is due and has an executable line; produced by AlgoRebalancingService.notifyActionable.
   */
  REBALANCE_DRIFT((byte) 5),
  /** A risk control forced an exit; produced by AlgoMeanReversionEvaluationService. */
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
  EXPRESSION((byte) 11),
  /**
   * A class, an instrument or the exposure of the monitored hierarchy left its tolerance band between two checkpoints;
   * produced by AlgoRebalancingService. A notification only: unlike {@link #REBALANCE_DRIFT} it asks for no trade.
   */
  ALLOCATION_BREACH((byte) 12);

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
