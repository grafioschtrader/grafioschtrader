package grafioschtrader.reportviews;

import java.util.Objects;

/**
 * Immutable key class representing a currency pair for exchange rate lookups and conversions.
 * Used as a composite key in maps and collections to efficiently organize and retrieve 
 * currency pair data based on source and target currencies.
 * 
 * <p>This class serves as a fundamental building block for currency conversion operations
 * in financial reporting, portfolio management, and transaction processing. It provides
 * a standardized way to represent currency relationships and enables efficient lookup
 * of exchange rates and currency pair metadata.</p>
 **/ 
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
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
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