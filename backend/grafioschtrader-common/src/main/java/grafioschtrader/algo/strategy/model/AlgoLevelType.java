package grafioschtrader.algo.strategy.model;

public enum AlgoLevelType {

  TOP_LEVEL(StrategyHelper.TOP_LEVEL_LETTER), ASSET_CLASS_LEVEL(StrategyHelper.ASSET_CLASS_LEVEL_LETTER),
  SECURITY_LEVEL(StrategyHelper.SECURITY_LEVEL_LETTER);

  private final String level;

  AlgoLevelType(String level) {
    this.level = level;
  }

  public String getValue() {
    return level;
  }

  public static AlgoLevelType getAlgoLeveType(String value) {
    for (AlgoLevelType algoLevelType : AlgoLevelType.values()) {
      if (algoLevelType.getValue().equals(value)) {
        return algoLevelType;
      }
    }
    return null;
  }
}
