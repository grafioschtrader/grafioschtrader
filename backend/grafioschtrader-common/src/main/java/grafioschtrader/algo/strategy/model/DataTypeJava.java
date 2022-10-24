package grafioschtrader.algo.strategy.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public enum DataTypeJava {
  Boolean(boolean.class),
  BooleanC(Boolean.class),
  Byte(byte.class),
  ByteC(Byte.class),
  IntegerC(Integer.class), 
  StringC(String.class), 
  LocalDate(LocalDate.class), 
  LccalDateTime(LocalDateTime.class),
  UNKNOWN(null);

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
