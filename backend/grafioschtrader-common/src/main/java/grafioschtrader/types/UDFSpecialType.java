package grafioschtrader.types;

/**
 * These values are saved in the database and should not be changed.
 */
public enum UDFSpecialType {
  // Only for bonds, yield to maturity
  UDF_SPEC_INTERNAL_CALC_YIELD_TO_MATURITY((byte) 1), 
  // Link to Yahoo earning web page 
  UDF_SPEC_INTERNAL_YAHOO_EARNING_LINK((byte) 2),
  //For the next earning date
  UDF_SPEC_INTERNAL_YAHOO_EARNING_NEXT_DATE((byte) 3),
  // This is just YAHOO earning to avoid having to determine the symbol each time.
  // Because this can be time consuming.
  UDF_SPEC_INTERNAL_YAHOO_SYMBOL_HIDE((byte) 4),
  // Link to Yahoo website statistics   
  UDF_SPEC_INTERNAL_YAHOO_STATISTICS_LINK((byte) 5);

  private final Byte value;

  private UDFSpecialType(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static UDFSpecialType getUDFSpecialTypeByValue(byte value) {
    for (UDFSpecialType uDFSpcialType : UDFSpecialType.values()) {
      if (uDFSpcialType.getValue() == value) {
        return uDFSpcialType;
      }
    }
    return null;
  }
}
