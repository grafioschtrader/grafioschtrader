package grafioschtrader.reportviews.account;

import java.time.LocalDate;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Identifies a currency for which no exchange rate into the main currency could be determined.
 *
 * <p>
 * A report that encounters this cannot convert the affected cash account, so it reports the account with its native
 * balance but leaves it out of every main-currency total. Instances of this class are collected per report and handed
 * to the client, which turns them into a warning telling the user which part of the result is incomplete and why.
 * </p>
 */
@Schema(description = """
    Identifies a currency for which no exchange rate to the main currency could be determined on the reporting date.
    The affected cash account is still reported with its own balance, but it is excluded from every main currency
    total.""")
public class MissingExchangeRate {

  @Schema(description = "Currency that could not be converted, ISO 4217")
  public String fromCurrency;

  @Schema(description = "Main currency of the tenant or portfolio into which the conversion failed, ISO 4217")
  public String toCurrency;

  @Schema(description = "Date for which the exchange rate was required")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public LocalDate date;

  @Schema(description = """
      Id of the currency pair. It is null when no currency pair exists at all for this combination, as opposed to a
      currency pair that exists but carries no price for the requested date.""")
  public Integer idCurrencypair;

  public MissingExchangeRate(String fromCurrency, String toCurrency, LocalDate date, Integer idCurrencypair) {
    this.fromCurrency = fromCurrency;
    this.toCurrency = toCurrency;
    this.date = date;
    this.idCurrencypair = idCurrencypair;
  }

  /**
   * Equality is on the currency combination and the date only, so that the same missing rate reported from several
   * transactions of the same account collapses into a single warning.
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof MissingExchangeRate other)) {
      return false;
    }
    return Objects.equals(fromCurrency, other.fromCurrency) && Objects.equals(toCurrency, other.toCurrency)
        && Objects.equals(date, other.date);
  }

  @Override
  public int hashCode() {
    return Objects.hash(fromCurrency, toCurrency, date);
  }

  @Override
  public String toString() {
    return "MissingExchangeRate [fromCurrency=" + fromCurrency + ", toCurrency=" + toCurrency + ", date=" + date
        + ", idCurrencypair=" + idCurrencypair + "]";
  }
}
