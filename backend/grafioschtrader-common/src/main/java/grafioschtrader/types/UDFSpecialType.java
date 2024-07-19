package grafioschtrader.types;

public enum UDFSpecialType {
  UDF_SPEC_INTERNAL_CALC_YIELD_TO_MATURITY((byte) 1);

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
