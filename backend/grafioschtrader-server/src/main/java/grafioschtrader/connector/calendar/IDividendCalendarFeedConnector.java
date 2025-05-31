package grafioschtrader.connector.calendar;

import java.time.LocalDate;
import java.util.List;

import grafioschtrader.entities.Dividend;
import grafioschtrader.types.CreateType;

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

  int getPriority();

  boolean supportISIN();

  public class CalendarDividends {

    public CalendarDividends(String name, LocalDate exDate, LocalDate payDate, double amount) {
      super();
      this.name = name;
      this.exDate = exDate;
      this.payDate = payDate;
      this.amount = amount;
    }

    public String name;
    public String ticker;
    public String isin;
    public LocalDate exDate;
    public LocalDate payDate;

    public double amount;
    public String currency;

    public Dividend getDivident(Integer idSecurity) {
      return new Dividend(idSecurity, exDate, payDate, amount, amount, currency, CreateType.CONNECTOR_CREATED);
    }
  }
}
