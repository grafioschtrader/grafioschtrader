package grafioschtrader.entities;

import grafiosch.common.PropertySelectiveUpdatableOrWhenNull;
import grafioschtrader.validation.ValidCurrencyCode;
import grafioschtrader.validation.ValidISIN;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Instrument pool entry for securities in the GT-Network.
 *
 * Securities are identified by their ISIN (International Securities Identification Number) and currency,
 * enabling cross-system matching regardless of internal IDs. This allows different Grafioschtrader instances
 * to share price data for the same security even if they have different idSecurity values.
 *
 * <h3>Usage</h3>
 * <ul>
 *   <li>Used by {@link GTNetLastprice} for intraday price data</li>
 *   <li>Used by {@link GTNetHistoryquote} for historical price data (foreign instruments only)</li>
 *   <li>When {@link #isLocalInstrument()} returns true, historical data goes to {@link Historyquote} instead</li>
 * </ul>
 *
 * @see GTNetInstrument for base fields and local/foreign distinction
 * @see GTNetInstrumentCurrencypair for currency pair instruments
 */
@Entity
@Table(name = GTNetInstrumentSecurity.TABNAME)
@DiscriminatorValue("S")
@Schema(description = """
    Instrument pool entry for securities in the GT-Network. Securities are identified by ISIN and currency,
    enabling cross-system matching regardless of internal IDs. When idSecuritycurrency is set, this security
    exists locally. Price data is stored in GTNetLastprice (intraday) and either historyquote (local) or
    gt_net_historyquote (foreign) for historical data.""")
public class GTNetInstrumentSecurity extends GTNetInstrument {
  public static final String TABNAME = "gt_net_instrument_security";

  @Schema(description = "International Securities Identification Number, ISO 6166")
  @ValidISIN
  @Column(name = "isin")
  @PropertySelectiveUpdatableOrWhenNull
  private String isin;

  @Schema(description = "Currency of security, ISO 4217")
  @ValidCurrencyCode
  @Column(name = "currency")
  @PropertySelectiveUpdatableOrWhenNull
  private String currency;

  public GTNetInstrumentSecurity() {
    super();
  }

  public String getIsin() {
    return isin;
  }

  public void setIsin(String isin) {
    this.isin = isin;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  @Override
  public String getInstrumentKey() {
    return isin + ":" + currency;
  }

}
