package grafioschtrader.entities;

import java.time.LocalDate;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Schema(description = "Contains the exchange rates of the European Central Bank. The prices usually start at around 14:10 CET.")
@Entity
@Table(name = EcbExchangeRates.TABNAME)
public class EcbExchangeRates {

  public static final String TABNAME = "ecb_exchange_rates";

  @EmbeddedId
  private DateCurrencyKey dateCurrencyKey;

  @Schema(description = "The EUR exchange rate to this currency")
  @Column(name = "rate")
  private double rate;

  public EcbExchangeRates() {
  }

  public EcbExchangeRates(LocalDate date, String currency, double rate) {
    this.dateCurrencyKey = new DateCurrencyKey(date, currency);
    this.rate = rate;
  }

  public DateCurrencyKey getDateCurrencyKey() {
    return dateCurrencyKey;
  }

  public static class DateCurrencyKey {
    @Schema(description = "Date of the exchange rate currency")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = BaseConstants.STANDARD_DATE_FORMAT)
    @Column(name = "date")
    public LocalDate date;

    @Schema(description = "The base currency EUR for this exchange rate currency as an ISO code")
    @Column(name = "currency")
    public String currency;

    public DateCurrencyKey() {
    }

    public DateCurrencyKey(LocalDate date, String currency) {
      this.date = date;
      this.currency = currency;
    }

    @Override
    public int hashCode() {
      return Objects.hash(currency, date);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if ((obj == null) || (getClass() != obj.getClass())) {
        return false;
      }
      DateCurrencyKey other = (DateCurrencyKey) obj;
      return Objects.equals(currency, other.currency) && Objects.equals(date, other.date);
    }

  }
}
