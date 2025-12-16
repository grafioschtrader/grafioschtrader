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
 * Intraday price data for securities shared via the GT-Network.
 *
 * Securities are identified by their ISIN (International Securities Identification Number) and currency,
 * enabling cross-system matching regardless of internal IDs. This allows different Grafioschtrader instances
 * to share price data for the same security even if they have different idSecurity values.
 *
 * @see GTNetLastprice for base OHLCV fields
 * @see GTNetLastpriceCurrencypair for currency pair prices
 */
@Entity
@Table(name = GTNetLastpriceSecurity.TABNAME)
@DiscriminatorValue("S")
@Schema(description = """
    Intraday price data for securities shared via the GT-Network. Securities are identified by ISIN and currency,
    enabling cross-system matching regardless of internal IDs. Contains the standard OHLCV fields inherited from
    GTNetLastprice, plus the security-specific identifiers.""")
public class GTNetLastpriceSecurity extends GTNetLastprice {
  public static final String TABNAME = "gt_net_lastprice_security";

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

  public GTNetLastpriceSecurity() {
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

}
