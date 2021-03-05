package grafioschtrader.algo.strategy.model;

public enum AlgoStrategyImplementations {

  // Must have a definition on every level
  AS_REBALANCING((byte) 1),
  // Can be used with other strategies
  AS_ABSOLUTE_PRICE_ALERT((byte) 65), AS_HOLDING_GAIN_LOSE_PERCENTAGE_ALERT((byte) 66),
  AS_PERIOD_PRICE_GAIN_LOSE_PERCENT_ALERT((byte) 67);

  private final Byte value;

  private AlgoStrategyImplementations(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static AlgoStrategyImplementations getAlgoStrategyImplentaions(byte value) {

    for (AlgoStrategyImplementations algoStrategyImplementations : AlgoStrategyImplementations.values()) {
      if (algoStrategyImplementations.getValue() == value) {
        return algoStrategyImplementations;
      }
    }
    return null;
  }
}
