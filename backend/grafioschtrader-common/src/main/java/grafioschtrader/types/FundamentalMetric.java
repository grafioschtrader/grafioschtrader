package grafioschtrader.types;

/**
 * Base value of a company's accounts stored per security and source in {@code security_fundamental}. Ratios are never
 * stored; a watchlist indicator column computes them with the live price. {@link #PE_CHECK} and {@link #MCAP_CHECK} are
 * evidence for the source checks only and are never selected or bound to an expression variable.
 */
public enum FundamentalMetric {
  /** Earnings per share of the trailing twelve months, in the stated currency per share of the listing */
  EPS_TTM((byte) 1),
  /** Earnings per share of the last fiscal year */
  EPS_FY((byte) 2),
  /** Book value per share */
  BVPS((byte) 3),
  /** Revenue per share of the trailing twelve months */
  SPS_TTM((byte) 4),
  /** Revenue per share of the last fiscal year */
  SPS_FY((byte) 5),
  /** Number of shares outstanding of the class of the listing; ADRs count in ADR equivalents */
  SHARES((byte) 6),
  /** Total debt minus cash, in full units */
  NET_DEBT((byte) 7),
  /** Yahoo's trailing price/earnings ratio, evidence for the check of EPS_TTM against the price */
  PE_CHECK((byte) 8),
  /** Yahoo's market capitalisation in its quote currency, evidence for the check of SHARES */
  MCAP_CHECK((byte) 9);

  private final byte value;

  FundamentalMetric(byte value) {
    this.value = value;
  }

  public byte getValue() {
    return value;
  }

  public static FundamentalMetric getByValue(byte value) {
    for (var item : values())
      if (item.value == value)
        return item;
    throw new IllegalArgumentException("Unknown FundamentalMetric value: " + value);
  }
}
