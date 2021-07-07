package grafioschtrader.reportviews;

import java.time.LocalDate;
import java.util.Objects;

public class FromToCurrencyWithDate extends FromToCurrency {
  final protected LocalDate date;

  public FromToCurrencyWithDate(String fromCurrency, String toCurrency, LocalDate date) {
    super(fromCurrency, toCurrency);
    this.date = date;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass() || !super.equals(o))
      return false;
    FromToCurrencyWithDate that = (FromToCurrencyWithDate) o;
    return Objects.equals(date, that.date);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), date);
  }

}
