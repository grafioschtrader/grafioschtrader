package grafioschtrader.entities;

import grafioschtrader.common.PropertySelectiveUpdatableOrWhenNull;
import grafioschtrader.validation.ValidCurrencyCode;
import grafioschtrader.validation.ValidISIN;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = GTNetLastpriceSecurity.TABNAME)
@DiscriminatorValue("S")
@Schema(description = "Contains the intraday last price for security")
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
