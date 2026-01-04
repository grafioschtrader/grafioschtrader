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
 * Instrument pool entry for currency pairs in the GT-Network.
 *
 * Currency pairs are identified by the combination of fromCurrency and toCurrency (both ISO 4217 codes),
 * enabling cross-system matching. The exchange rate represents how many units of toCurrency equal one
 * unit of fromCurrency (e.g., EUR/USD = 1.10 means 1 EUR = 1.10 USD).
 *
 * <h3>Usage</h3>
 * <ul>
 *   <li>Used by {@link GTNetLastprice} for intraday exchange rate data</li>
 *   <li>Used by {@link GTNetHistoryquote} for historical exchange rates (foreign instruments only)</li>
 *   <li>When {@link #isLocalInstrument()} returns true, historical data goes to {@link Historyquote} instead</li>
 * </ul>
 *
 * @see GTNetInstrument for base fields and local/foreign distinction
 * @see GTNetInstrumentSecurity for security instruments
 */
@Entity
@Table(name = GTNetInstrumentCurrencypair.TABNAME)
@DiscriminatorValue("C")
@Schema(description = """
    Instrument pool entry for currency pairs in the GT-Network. Currency pairs are identified by from/to
    currency codes (ISO 4217), enabling cross-system matching. When idSecuritycurrency is set, this pair
    exists locally. Price data is stored in GTNetLastprice (intraday) and either historyquote (local) or
    gt_net_historyquote (foreign) for historical data.""")
public class GTNetInstrumentCurrencypair extends GTNetInstrument {

  public static final String TABNAME = "gt_net_instrument_currencypair";

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

  public GTNetInstrumentCurrencypair() {
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

  @Override
  public String getInstrumentKey() {
    return fromCurrency + ":" + toCurrency;
  }

}
