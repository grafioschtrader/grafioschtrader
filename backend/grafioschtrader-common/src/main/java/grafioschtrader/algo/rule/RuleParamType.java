package grafioschtrader.algo.rule;

public enum RuleParamType {
  RP_PERCENTAGE((byte) 1), RP_ABSOLUTE_PRICE((byte) 2), RP_CLOSE_PRICE((byte) 3), RP_SMA((byte) 4), RP_EMA((byte) 5);

  private final Byte value;

  private RuleParamType(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static RuleParamType getRuleParamType(byte value) {
    for (RuleParamType ruleParams : RuleParamType.values()) {
      if (ruleParams.getValue() == value) {
        return ruleParams;
      }
    }
    return null;
  }
}
