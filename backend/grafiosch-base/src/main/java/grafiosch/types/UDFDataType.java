package grafiosch.types;

/**
 * This is the selection of data type for user defined fields. These are offered
 * to the user and then mapped to the internal data types. The values must
 * correspond to the DataType of {@link grafiosch.dynamic.model.DataType}
 */
public enum UDFDataType {

  // Decimal numbers
  UDF_Numeric((byte) 1),
  // Only Integer
  UDF_NumericInteger((byte) 4),
  // Only String
  UDF_String((byte) 7),
  // Date with time
  UDF_DateTimeNumeric((byte) 8),
  // Only Date
  UDF_DateString((byte) 10),
  // True or false
  UDF_Boolean((byte) 13),
  // For a web link. Normally this is a string with validation for validity as a
  // URL.
  UDF_URLString((byte) 20);

  private final Byte value;

  private UDFDataType(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static UDFDataType getUDFDataType(byte value) {
    for (UDFDataType uDFDataType : UDFDataType.values()) {
      if (uDFDataType.getValue() == value) {
        return uDFDataType;
      }
    }
    return null;
  }
}
