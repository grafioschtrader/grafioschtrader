package grafioschtrader.types;

/**
 * Outcome of one security search against one data provider. A search never throws: every failure is reported through
 * one of these values, so that a caller merging several providers can keep the results of the others and tell the
 * user why a provider contributed nothing.
 */
public enum SecuritySearchStatus {

  /** The provider answered and at least one candidate survived the identity checks. */
  OK,

  /**
   * The provider answered but delivered no candidate, or every candidate contradicted the supplied ISIN, currency or
   * MIC.
   */
  NO_MATCH,

  /**
   * The provider cannot answer this kind of query, for example a single-exchange provider asked for another MIC or a
   * query without ISIN and ticker.
   */
  UNSUPPORTED_QUERY,

  /** The provider needs an API key and none is configured. */
  MISSING_CREDENTIALS,

  /** The provider refused the request because the configured access does not cover this endpoint or market. */
  NOT_ENTITLED,

  /** The provider signalled too many requests, or the local request budget had no token within the waiting limit. */
  RATE_LIMITED,

  /** The provider did not answer within the request timeout. */
  TIMEOUT,

  /** Any other failure: a server error, an I/O error or a response that could not be parsed. */
  PROVIDER_ERROR;

  /**
   * Tells whether the outcome says nothing about the instrument because the provider could not be asked properly. A
   * caller must not cache or persist the absence of a result in that case, since a later attempt may well succeed.
   *
   * @return true for {@link #RATE_LIMITED}, {@link #TIMEOUT} and {@link #PROVIDER_ERROR}
   */
  public boolean isTransient() {
    return this == RATE_LIMITED || this == TIMEOUT || this == PROVIDER_ERROR;
  }
}
