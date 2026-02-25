package grafioschtrader.algo.strategy.model;

public enum AlgoStrategyImplementationType {

  // Must have a definition on every level
  // -------------------------------------
  AS_REBALANCING((byte) 1),
  // Can be used with other strategies
  // ---------------------------------

  AS_ABSOLUTE_PRICE_ALERT((byte) 65),
  // If the portfolio, asset class or security held loses a certain percentage.
  AS_HOLDING_GAIN_LOSE_PERCENTAGE_ALERT((byte) 66),
  // If the asset class or security gains or loses a certain percentage in a given
  // period.
  AS_PERIOD_PRICE_GAIN_LOSE_PERCENT_ALERT((byte) 67),
  // Complex strategy with JSON config: mean reversion dip buying
  AS_MEAN_REVERSION_DIP((byte) 68),
  // Watchlist alarm: price vs moving average crossing
  AS_MA_CROSSING_ALERT((byte) 69),
  // Watchlist alarm: RSI threshold breach
  AS_RSI_THRESHOLD_ALERT((byte) 70),
  // Watchlist alarm: custom EvalEx expression
  AS_EXPRESSION_ALERT((byte) 71);

  private final Byte value;

  private AlgoStrategyImplementationType(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static AlgoStrategyImplementationType getAlgoStrategyImplentaionType(byte value) {
    for (AlgoStrategyImplementationType algoStrategyImplementations : AlgoStrategyImplementationType.values()) {
      if (algoStrategyImplementations.getValue() == value) {
        return algoStrategyImplementations;
      }
    }
    return null;
  }
}
