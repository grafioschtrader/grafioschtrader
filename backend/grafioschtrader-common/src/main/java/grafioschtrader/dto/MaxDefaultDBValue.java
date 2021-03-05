package grafioschtrader.dto;

public class MaxDefaultDBValue {
  int defaultValue;
  Integer dbValue;

  public MaxDefaultDBValue(Integer defaultValue) {
    super();
    this.defaultValue = defaultValue;
  }

  public Integer getDbValue() {
    return dbValue;
  }

  public void setDbValue(Integer dbValue) {
    this.dbValue = dbValue;
  }

  public int getDefaultValue() {
    return defaultValue;
  }

}
