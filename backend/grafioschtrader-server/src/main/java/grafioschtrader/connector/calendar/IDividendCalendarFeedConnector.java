package grafioschtrader.connector.calendar;

import java.time.LocalDate;
import java.util.List;

import grafioschtrader.entities.Dividend;
import grafioschtrader.types.CreateType;

/**
 * Interface for dividend calendar feed connectors that retrieve dividend information from external data sources.
 * Implementations provide dividend data for specific ex-dividend dates with support for prioritization and different
 * security identifiers.
 */
public interface IDividendCalendarFeedConnector {

  /**
   * Retrieves a list of calendar dividends for a specified ex-date.
   *
   * @param exDate the ex-date for which to retrieve dividends, specified as a {@link LocalDate}.
   * @return a list of {@link CalendarDividends} objects representing the dividends associated with the specified
   *         ex-date.
   * @throws Exception if an error occurs while fetching or processing the data, such as issues with the URL, data
   *                   deserialization, or other runtime exceptions.
   **/
  List<CalendarDividends> getExDateDividend(LocalDate exDate) throws Exception;

  /**
   * Returns the priority ranking of this split calendar connector. Lower numbers indicate higher priority - connectors
   * with priority 1 are consulted before those with priority 2, etc.
   * 
   * @return integer representing the priority of this connector (lower = higher priority)
   */
  int getPriority();

  /**
   * Indicates whether this connector supports ISIN-based security identification.
   * 
   * @return true if ISIN identification is supported, false otherwise
   */
  boolean supportISIN();

  /**
   * Data transfer object representing dividend information from a calendar feed. Contains security identification,
   * timing, and financial details for dividends.
   */
  public class CalendarDividends {
    public String name;
    public String ticker;
    public String isin;
    public LocalDate exDate;
    public LocalDate payDate;
    public double amount;
    public String currency;

    /**
     * Constructs a CalendarDividends object with essential dividend information.
     * 
     * @param name    the company or security name
     * @param exDate  the ex-dividend date
     * @param payDate the payment date
     * @param amount  the dividend amount per share
     */
    public CalendarDividends(String name, LocalDate exDate, LocalDate payDate, double amount) {
      super();
      this.name = name;
      this.exDate = exDate;
      this.payDate = payDate;
      this.amount = amount;
    }

    public Dividend getDivident(Integer idSecurity) {
      return new Dividend(idSecurity, exDate, payDate, amount, amount, currency, CreateType.CONNECTOR_CREATED);
    }
  }
}
