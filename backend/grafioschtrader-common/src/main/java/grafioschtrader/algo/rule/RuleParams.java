package grafioschtrader.algo.rule;

public enum RuleParams {
  RP_PERCENTAGE((byte) 1), RP_ABSOLUTE_PRICE((byte) 2), RP_CLOSE_PRICE((byte) 3), RP_SMA((byte) 4), RP_EMA((byte) 5);

  private final Byte value;

  private RuleParams(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static RuleParams getTradingRules(byte value) {
    for (RuleParams ruleParams : RuleParams.values()) {
      if (ruleParams.getValue() == value) {
        return ruleParams;
      }
    }
    return null;
  }
}
