package grafiosch.dynamic.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Used for mapping between Java data types and GUI data types.
 */
public enum DataTypeJava {
  Boolean(boolean.class), BooleanC(Boolean.class), Byte(byte.class), ByteC(Byte.class), Double(double.class),
  DoubleC(Double.class), Integer(Integer.class), IntegerC(Integer.class), StringC(String.class),
  LocalDate(LocalDate.class), LocalDateTime(LocalDateTime.class), UNKNOWN(null);

  private final Class<?> targetClass;

  private DataTypeJava(Class<?> targetClass) {
    this.targetClass = targetClass;
  }

  public static DataTypeJava fromClass(Class<?> cls) {
    for (DataTypeJava c : values()) {
      if (c.targetClass == cls) {
        return c;
      }
    }
    return UNKNOWN;
  }
}
