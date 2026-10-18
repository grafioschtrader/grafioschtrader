package grafioschtrader.connector.calendar;

import java.time.LocalDate;
import java.util.Map;

import grafioschtrader.entities.Securitysplit;

/**
 * Interface for split calendar feed connectors that retrieve stock split information from external data sources.
 * Implementations provide split data for specific dates with support for country filtering and connector
 * prioritization.
 */
public interface ISplitCalendarFeedConnector {

  /**
   * Retrieves stock split data for all securities on the specified date. Results can be filtered by country codes to
   * limit the scope of data retrieval.
   *
   * @param forDate     the date for which to retrieve split information
   * @param countyCodes array of country codes to filter results, or null for all countries
   * @return map of ticker symbols to TickerSecuritysplit objects containing split data
   * @throws Exception if data retrieval fails due to network issues, authentication problems, or data provider errors
   */
  Map<String, TickerSecuritysplit> getCalendarSplitForSingleDay(LocalDate forDate, String[] countyCodes)
      throws Exception;

  /**
   * Returns the priority ranking of this split calendar connector. Lower numbers indicate higher priority - connectors
   * with priority 1 are consulted before those with priority 2, etc.
   *
   * @return integer representing the priority of this connector (lower = higher priority)
   */
  int getPriority();

  /**
   * Data transfer object representing stock split information with company details. Contains company identification and
   * the associated security split data.
   */
  class TickerSecuritysplit {

    /** Company name for identification */
    public String companyName;

    /**
     * ISO country code of the listing that splits, or null when the connector does not know it. A depositary receipt
     * often carries the ticker of its underlying stock, so without this the split of a Canadian CDR would be matched
     * with the US stock.
     */
    public String countryCode;

    /**
     * Yahoo exchange suffix of the listing including the dot, for example ".SW", an empty string for a listing without
     * suffix (US exchanges), or null when the connector does not deliver it. Compared with the Yahoo suffix of the
     * security's stock exchange.
     */
    public String symbolSuffix;

    /** Security split information including dates, ratios, and details */
    public Securitysplit securitysplit;

    public TickerSecuritysplit(String companyName, Securitysplit securitysplit) {
      this.companyName = companyName;
      this.securitysplit = securitysplit;
    }

    /**
     * Creates the split information together with the identification of the listing.
     *
     * @param companyName   company name for the similarity check with the security name
     * @param countryCode   ISO country code of the listing, or null if unknown
     * @param symbolSuffix  Yahoo exchange suffix of the listing, or null if unknown
     * @param securitysplit the announced split
     */
    public TickerSecuritysplit(String companyName, String countryCode, String symbolSuffix,
        Securitysplit securitysplit) {
      this(companyName, securitysplit);
      this.countryCode = countryCode;
      this.symbolSuffix = symbolSuffix;
    }

    @Override
    public String toString() {
      return "TickerSecuritysplit [companyName=" + companyName + ", countryCode=" + countryCode + ", symbolSuffix="
          + symbolSuffix + ", securitysplit=" + securitysplit + "]";
    }

  }

}
