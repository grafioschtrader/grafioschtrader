package grafioschtrader.dto;

import java.util.Objects;

public class SecurityKeyISINCurrency {
  private final String isin;
  private final String currency;

  public SecurityKeyISINCurrency(String isin, String currency) {
    this.isin = isin;
    this.currency = currency;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SecurityKeyISINCurrency that = (SecurityKeyISINCurrency) o;
    return Objects.equals(isin, that.isin) && Objects.equals(currency, that.currency);
  }

  @Override
  public int hashCode() {
    return Objects.hash(isin, currency);
  }

  @Override
  public String toString() {
    return isin + "_" + currency;
  }
}