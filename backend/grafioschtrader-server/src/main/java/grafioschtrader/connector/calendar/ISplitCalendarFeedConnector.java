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
    
    /** Country code where the company is listed */
    public String countryCode;
    
    /** Security split information including dates, ratios, and details */
    public Securitysplit securitysplit;

    public TickerSecuritysplit(String companyName, Securitysplit securitysplit) {
      this.companyName = companyName;
      this.securitysplit = securitysplit;
    }

    @Override
    public String toString() {
      return "TickerSecuritysplit [companyName=" + companyName + ", countryCode=" + countryCode + ", securitysplit="
          + securitysplit + "]";
    }

  }

}
