package grafioschtrader.algo.rule;

public enum TradingRuleType {
  // Buy, Sell -> Param1: Price/SMA/EMA, Param2: ABSOLUTE VALUE/SMA/EMA
  RULE_CROSSED_DOWN((byte) 1),

  // Buy, Sell -> Param1: Price/SMA/EMA, Param2: ABSOLUTE VALUE/SMA/EMA
  RULE_CROSSED_UP((byte) 2),

  // Buy, Sell -> Param1: BUY/SELL, Param2: PERIOD
  RULE_WAIT_PERIOD((byte) 3),

  // Sell -> Param1: Price, Param2: PERCENTAGE VALUE
  RULE_STOP_LOSS((byte) 4),

  // Sell -> Param1: Price, Param2: PERCENTABE VALUE
  RULE_STOP_GAIN((byte) 5);

  private final Byte value;

  private TradingRuleType(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static TradingRuleType getTradingRuleType(byte value) {
    for (TradingRuleType tradingRules : TradingRuleType.values()) {
      if (tradingRules.getValue() == value) {
        return tradingRules;
      }
    }
    return null;
  }
}
