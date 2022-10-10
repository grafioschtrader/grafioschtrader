package grafioschtrader.entities;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import grafioschtrader.validation.ValidCurrencyCode;

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
