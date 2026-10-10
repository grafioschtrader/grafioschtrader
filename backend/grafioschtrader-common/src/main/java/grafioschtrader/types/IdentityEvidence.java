package grafioschtrader.types;

/**
 * How firmly a provider search established one identity field (ISIN, currency or MIC) of a candidate. The distinction
 * matters because asking a provider for an ISIN does not mean that its answer confirms that ISIN: a full-text search
 * may return instruments that merely resemble the query.
 */
public enum IdentityEvidence {

  /** The provider delivered the value in its response, and it does not contradict the query. */
  RETURNED,

  /**
   * The provider applied the value as an exact server-side filter, for example the OpenFIGI mapping job
   * {@code idType=ID_ISIN} with {@code micCode}. The response does not repeat the value, but the provider would not
   * have answered with this instrument otherwise.
   */
  FILTERED,

  /** The value is neither delivered nor filtered by the provider; it stays unknown and must not be assumed. */
  UNKNOWN;

  /**
   * Tells whether the field can be relied upon for identifying the instrument.
   *
   * @return true for {@link #RETURNED} and {@link #FILTERED}
   */
  public boolean isEstablished() {
    return this != UNKNOWN;
  }
}
