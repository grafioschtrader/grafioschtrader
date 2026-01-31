package grafioschtrader.types;

/**
 * Enum constants representing types of gaps (mismatches) that can occur during GTNet security import.
 * When a security cannot be fully matched from GTNet peers, gap records document what specifically
 * didn't match.
 */
public enum GapCodeType {

  /**
   * Asset class combination (categoryType, specialInvestmentInstrument, subCategoryNLS) does not match
   * any local asset class configuration.
   */
  ASSET_CLASS((byte) 0),

  /**
   * Intraday/last price connector is not available locally for the security.
   */
  INTRADAY_CONNECTOR((byte) 1),

  /**
   * Historical price data connector is not available locally for the security.
   */
  HISTORY_CONNECTOR((byte) 2),

  /**
   * Dividend data connector is not available locally for the security.
   */
  DIVIDEND_CONNECTOR((byte) 3),

  /**
   * Split data connector is not available locally for the security.
   */
  SPLIT_CONNECTOR((byte) 4);

  private final Byte value;

  private GapCodeType(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  /**
   * Returns the GapCodeType for the given byte value.
   *
   * @param value the byte value to look up
   * @return the matching GapCodeType, or null if no match found
   */
  public static GapCodeType getGapCodeTypeByValue(byte value) {
    for (GapCodeType gapCodeType : GapCodeType.values()) {
      if (gapCodeType.getValue() == value) {
        return gapCodeType;
      }
    }
    return null;
  }
}
