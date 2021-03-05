package grafioschtrader.algo.strategy.model;

import java.time.LocalDate;

public enum DataTypeJava {
  IntegerC(Integer.class), StringC(String.class), LocalDate(LocalDate.class), UNKNOWN(null);

  private final Class<?> targetClass;

  private DataTypeJava(Class<?> targetClass) {

    this.targetClass = targetClass;
  }

  public static DataTypeJava fromClass(Class<?> cls) {
    for (DataTypeJava c : values()) {
      if (c.targetClass == cls)
        return c;
    }
    return UNKNOWN;
  }
}
