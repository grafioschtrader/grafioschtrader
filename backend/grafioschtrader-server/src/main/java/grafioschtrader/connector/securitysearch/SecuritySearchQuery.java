package grafioschtrader.connector.securitysearch;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What is known about an instrument when a provider search is started. The ISIN is the primary key of the search;
 * currency and MIC narrow it down and may be unknown, for example during a transaction import. Ticker and name are only
 * hints for a fallback query and never count as identity.
 *
 * <p>
 * The compact constructor trims every value, upper-cases ISIN and MIC, turns blank strings into null and replaces a
 * non-positive result limit by {@link #DEFAULT_MAX_RESULTS}. The currency keeps its case, because minor units such as
 * {@code GBp} are distinguished by it.
 * </p>
 *
 * @param isin       ISIN of the instrument, may be null
 * @param currency   trading currency, may be null when unknown
 * @param mic        MIC of the listing exchange, may be null when unknown
 * @param ticker     ticker symbol used as a fallback query, may be null
 * @param name       name of the instrument, may be null; not used by the current providers
 * @param maxResults upper bound of the candidates a single provider returns
 */
public record SecuritySearchQuery(String isin, String currency, String mic, String ticker, String name,
    int maxResults) {

  /** Number of candidates a provider returns when the caller sets no limit. */
  public static final int DEFAULT_MAX_RESULTS = 10;

  private static final Pattern ISIN_PATTERN = Pattern.compile("^[A-Z]{2}[A-Z0-9]{9}[0-9]$");

  public SecuritySearchQuery {
    isin = normalize(isin, true);
    currency = normalize(currency, false);
    mic = normalize(mic, true);
    ticker = normalize(ticker, false);
    name = normalize(name, false);
    maxResults = maxResults > 0 ? maxResults : DEFAULT_MAX_RESULTS;
  }

  /**
   * Creates a query with the default result limit.
   *
   * @param isin     ISIN of the instrument, may be null
   * @param currency trading currency, may be null
   * @param mic      MIC of the listing exchange, may be null
   * @param ticker   ticker symbol used as a fallback query, may be null
   * @return the query
   */
  public static SecuritySearchQuery of(String isin, String currency, String mic, String ticker) {
    return new SecuritySearchQuery(isin, currency, mic, ticker, null, DEFAULT_MAX_RESULTS);
  }

  /**
   * Tells whether the query carries a syntactically valid ISIN. Providers that place the ISIN into a URL or a filter
   * expression rely on this check, so an arbitrary string never reaches the provider.
   *
   * @return true when the ISIN consists of a two-letter country code, nine alphanumeric characters and a check digit
   */
  public boolean hasValidIsin() {
    return isin != null && ISIN_PATTERN.matcher(isin).matches();
  }

  private static String normalize(String value, boolean upperCase) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String trimmed = value.trim();
    return upperCase ? trimmed.toUpperCase(Locale.ROOT) : trimmed;
  }
}
