package grafioschtrader.reportviews;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Extended currency pair key that includes a specific date for time-sensitive exchange rate lookups.
 * Builds upon {@link FromToCurrency} by adding date information to enable historical exchange rate
 * retrieval and time-specific currency conversion operations.
 * 
 * <p>This class is essential for financial reporting and portfolio valuation where exchange rates
 * must be retrieved for specific dates rather than using current market rates. It enables accurate
 * historical calculations, performance analysis, and regulatory reporting that requires
 * point-in-time currency conversions.</p>
 */
public class FromToCurrencyWithDate extends FromToCurrency {
  final protected LocalDate date;

  /**
   * Creates a new date-specific currency pair with the specified source currency, target currency, and date.
   * 
   * @param fromCurrency the source currency code (e.g., "USD")
   * @param toCurrency the target currency code (e.g., "EUR")
   * @param date the specific date for the exchange rate lookup
   */
  public FromToCurrencyWithDate(String fromCurrency, String toCurrency, LocalDate date) {
    super(fromCurrency, toCurrency);
    this.date = date;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass() || !super.equals(o)) {
      return false;
    }
    FromToCurrencyWithDate that = (FromToCurrencyWithDate) o;
    return Objects.equals(date, that.date);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), date);
  }

}
