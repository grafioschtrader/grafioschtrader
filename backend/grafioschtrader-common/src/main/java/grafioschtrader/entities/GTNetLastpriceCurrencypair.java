package grafioschtrader.entities;

import grafioschtrader.validation.ValidCurrencyCode;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = GTNetLastpriceCurrencypair.TABNAME)
@DiscriminatorValue("C")
public class GTNetLastpriceCurrencypair extends GTNetLastprice {

  public static final String TABNAME = "gt_net_lastprice_currencypair";

  @Basic(optional = false)
  @NotNull
  @ValidCurrencyCode
  @Column(name = "from_currency")
  private String fromCurrency;

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
