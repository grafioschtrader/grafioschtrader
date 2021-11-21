package grafioschtrader.algo.rule.impl;

public class StopGainRule {
  private double gainPercentage;

  public StopGainRule(double gainPercentage) {
    super();
    this.gainPercentage = gainPercentage;
  }

  public boolean isStatisfied() {
    // TODO
    return true;
  }

  @Override
  public String toString() {
    return "StopGainRule [gainPercentage=" + gainPercentage + "]";
  }

}
