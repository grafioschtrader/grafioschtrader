/**
 * Enum representing types of gaps (mismatches) that can occur during GTNet security import.
 * When a security cannot be fully matched from GTNet peers, gap records document what
 * specifically didn't match.
 */
export enum GapCodeType {
  /**
   * Asset class combination (categoryType, specialInvestmentInstrument, subCategoryNLS)
   * does not match any local asset class configuration.
   */
  ASSET_CLASS = 0,

  /**
   * Intraday/last price connector is not available locally for the security.
   */
  INTRADAY_CONNECTOR = 1,

  /**
   * Historical price data connector is not available locally for the security.
   */
  HISTORY_CONNECTOR = 2,

  /**
   * Dividend data connector is not available locally for the security.
   */
  DIVIDEND_CONNECTOR = 3,

  /**
   * Split data connector is not available locally for the security.
   */
  SPLIT_CONNECTOR = 4
}
