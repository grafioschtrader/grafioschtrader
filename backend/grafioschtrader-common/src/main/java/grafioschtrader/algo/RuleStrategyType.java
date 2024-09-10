package grafioschtrader.algo;

public enum RuleStrategyType {
  RS_RULE((byte) 1), 
  
  RS_STRATEGY((byte) 2);

  private final Byte value;

  private RuleStrategyType(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static RuleStrategyType getRuleStrategyType(byte value) {
    for (RuleStrategyType ruleStrategy : RuleStrategyType.values()) {
      if (ruleStrategy.getValue() == value) {
        return ruleStrategy;
      }
    }
    return null;
  }

}
