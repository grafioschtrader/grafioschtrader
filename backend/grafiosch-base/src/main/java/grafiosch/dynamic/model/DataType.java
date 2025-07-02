package grafiosch.dynamic.model;

/**
 * Enumeration of data types used for GUI form generation and input validation. These data types are specifically
 * designed for user interface components and may differ from their corresponding Java data types to better represent
 * form input requirements and validation rules.
 * 
 * <p>
 * Each data type in this enum corresponds to a specific type of form input component and associated validation
 * behavior. The data types are designed to provide enough granularity for generating appropriate UI controls while
 * maintaining simplicity for form processing.
 * </p>
 * 
 * <p>
 * Unlike Java data types which focus on memory representation and operations, these GUI data types emphasize user
 * interaction patterns, input validation, and display formatting requirements.
 * </p>
 */
public enum DataType {

  /**
   * Represents an undefined or unspecified data type. Used as a fallback when no appropriate data type can be
   * determined or when a field should not be rendered in the form.
   */
  None((byte) 0),
  /** Decimal numbers */
  Numeric((byte) 1),
  /** Only Integer */
  NumericInteger((byte) 4),
  /** Only String */
  String((byte) 7),
  /** Date with time */
  DateTimeNumeric((byte) 8),
  /** Only Date */
  DateString((byte) 10),
  /** True or false */
  Boolean((byte) 13),
  /** For a web link. Normally this is a string with validation for validity as a URL. */
  URLString((byte) 20);

  private final Byte value;

  private DataType(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static DataType getDataType(byte value) {
    for (DataType dataType : DataType.values()) {
      if (dataType.getValue() == value) {
        return dataType;
      }
    }
    return null;
  }
}
