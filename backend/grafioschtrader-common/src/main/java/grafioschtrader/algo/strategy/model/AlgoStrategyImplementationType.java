package grafioschtrader.algo.strategy.model;

public enum AlgoStrategyImplementationType {

  // HOLDING strategies: evaluate positions held in the portfolio
  // Must have a definition on every level (TOP = all 3 levels)
  AS_HOLDING_TOP_REBALANCING((byte) 1),
  // Holdings gain/lose: percentage and/or absolute price thresholds (TOP = all 3 levels)
  AS_HOLDING_TOP_GAIN_LOSE((byte) 66),

  // OBSERVED strategies: monitor watchlist securities for buy signals (SECURITY level only)
  AS_OBSERVED_SECURITY_ABSOLUTE_PRICE((byte) 65),
  // Period price gain/lose: restricted to security level only
  AS_OBSERVED_SECURITY_PERIOD_PRICE_GAIN_LOSE_PERCENT((byte) 67),
  // Complex strategy with JSON config: mean reversion dip buying
  AS_OBSERVED_SECURITY_MEAN_REVERSION_DIP((byte) 68),
  // Price vs moving average crossing
  AS_OBSERVED_SECURITY_MA_CROSSING((byte) 69),
  // RSI threshold breach
  AS_OBSERVED_SECURITY_RSI_THRESHOLD((byte) 70),
  // Custom EvalEx expression
  AS_OBSERVED_SECURITY_EXPRESSION((byte) 71);

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
