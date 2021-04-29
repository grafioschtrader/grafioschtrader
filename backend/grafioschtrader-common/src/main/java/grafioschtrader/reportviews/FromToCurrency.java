package grafioschtrader.reportviews;

import java.util.Objects;

public class FromToCurrency {
  final protected String fromCurrency;
  final protected String toCurrency;

  public FromToCurrency(String fromCurrency, String toCurrency) {
    this.fromCurrency = fromCurrency;
    this.toCurrency = toCurrency;
  }

  public String getFromCurrency() {
    return fromCurrency;
  }

  public String getToCurrency() {
    return toCurrency;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    FromToCurrency that = (FromToCurrency) o;
    return Objects.equals(fromCurrency, that.fromCurrency) && Objects.equals(toCurrency, that.toCurrency);
  }

  @Override
  public int hashCode() {
    return Objects.hash(fromCurrency, toCurrency);
  }

  @Override
  public String toString() {
    return "FromToCurrency [fromCurrency=" + fromCurrency + ", toCurrency=" + toCurrency + "]";
  }

}