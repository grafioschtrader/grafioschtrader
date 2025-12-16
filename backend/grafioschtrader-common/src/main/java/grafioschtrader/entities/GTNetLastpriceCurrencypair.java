package grafioschtrader.entities;

import grafioschtrader.validation.ValidCurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

/**
 * Intraday price data for currency pairs shared via the GT-Network.
 *
 * Currency pairs are identified by the combination of fromCurrency and toCurrency (both ISO 4217 codes),
 * enabling cross-system matching. The exchange rate represents how many units of toCurrency equal one
 * unit of fromCurrency (e.g., EUR/USD = 1.10 means 1 EUR = 1.10 USD).
 *
 * @see GTNetLastprice for base OHLCV fields
 * @see GTNetLastpriceSecurity for security prices
 */
@Entity
@Table(name = GTNetLastpriceCurrencypair.TABNAME)
@DiscriminatorValue("C")
@Schema(description = """
    Intraday price data for currency pairs shared via the GT-Network. Currency pairs are identified by from/to
    currency codes (ISO 4217), enabling cross-system matching. The rate represents units of toCurrency per one
    unit of fromCurrency. Contains OHLCV fields inherited from GTNetLastprice.""")
public class GTNetLastpriceCurrencypair extends GTNetLastprice {

  public static final String TABNAME = "gt_net_lastprice_currencypair";

  @Schema(description = """
      Source currency code (ISO 4217, e.g., 'EUR', 'USD', 'CHF'). Combined with toCurrency, this uniquely
      identifies the currency pair. The exchange rate indicates how much toCurrency you get for one unit
      of this fromCurrency.""")
  @Basic(optional = false)
  @NotNull
  @ValidCurrencyCode
  @Column(name = "from_currency")
  private String fromCurrency;

  @Schema(description = """
      Target currency code (ISO 4217, e.g., 'EUR', 'USD', 'CHF'). Combined with fromCurrency, this uniquely
      identifies the currency pair. The 'last' price field indicates how many units of this currency equal
      one unit of fromCurrency.""")
  @Basic(optional = false)
  @NotNull
  @ValidCurrencyCode
  @Column(name = "to_currency")
  private String toCurrency;

  public GTNetLastpriceCurrencypair() {
    super();
  }

  public String getFromCurrency() {
    return fromCurrency;
  }

  public void setFromCurrency(String fromCurrency) {
    this.fromCurrency = fromCurrency;
  }

  public String getToCurrency() {
    return toCurrency;
  }

  public void setToCurrency(String toCurrency) {
    this.toCurrency = toCurrency;
  }

}
