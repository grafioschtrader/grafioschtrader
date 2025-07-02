package grafiosch.dynamic.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Enumeration that provides mapping between Java data types and form generation types.
 * This enum is used to identify and categorize Java class types for the purpose of
 * generating appropriate form input components and validation rules in dynamic forms.
 * 
  * <p>The enum distinguishes between primitive types and their corresponding wrapper classes
 * to ensure accurate type mapping during reflection-based form generation. For example,
 * {@code boolean.class} maps to {@link #Boolean} while {@code Boolean.class} maps to {@link #BooleanC}.</p>
 */
public enum DataTypeJava {
  
  /** Primitive boolean type ({@code boolean.class}) */
  Boolean(boolean.class), 
  
  /** Boolean wrapper class ({@code Boolean.class}) */
  BooleanC(Boolean.class), 
  
  /** Primitive byte type ({@code byte.class}) */
  Byte(byte.class), 
  
  /** Byte wrapper class ({@code Byte.class}) */
  ByteC(Byte.class), 
  
  /** Primitive double type ({@code double.class}) */
  Double(double.class),
  
  /** Double wrapper class ({@code Double.class}) */
  DoubleC(Double.class), 
  
  /** Integer wrapper class ({@code Integer.class}) */
  Integer(Integer.class), 
  
  /** Integer wrapper class ({@code Integer.class}) - alternative mapping */
  IntegerC(Integer.class), 
  
  /** String class ({@code String.class}) for text data */
  StringC(String.class),
  
  /** LocalDate class ({@code LocalDate.class}) for date-only values */
  LocalDate(LocalDate.class), 
  
  /** LocalDateTime class ({@code LocalDateTime.class}) for date and time values */
  LocalDateTime(LocalDateTime.class), 
  
  /** Represents an unknown or unsupported Java type */
  UNKNOWN(null);

  /** The Java class associated with this enum value */
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
