package grafioschtrader.algo;

public enum RuleStrategy {
  RS_RULE((byte) 1), RS_STRATEGY((byte) 2);

  private final Byte value;

  private RuleStrategy(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static RuleStrategy getRuleStrategy(byte value) {
    for (RuleStrategy ruleStrategy : RuleStrategy.values()) {
      if (ruleStrategy.getValue() == value) {
        return ruleStrategy;
      }
    }
    return null;
  }

}
