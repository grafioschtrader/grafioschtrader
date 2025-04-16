/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package grafioschtrader.entities;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.common.PropertySelectiveUpdatableOrWhenNull;
import grafiosch.entities.projection.IUDFSupport;
import grafioschtrader.GlobalConstants;
import grafioschtrader.validation.ValidCurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.NamedAttributeNode;
import jakarta.persistence.NamedEntityGraph;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

@Schema(description = """
Used for currency pairs, including cryptocurrencies. No transactions can be carried out on currencies. 
Currency pairs can be created by the user. 
In addition, the system also creates currency pairs, which is necessary for the performance calculations to work.""")
@Entity
@Table(name = Currencypair.TABNAME)
@DiscriminatorValue("C")
@NamedEntityGraph(name = "graph.currency.historyquote", attributeNodes = @NamedAttributeNode("historyquoteList"))
public class Currencypair extends Securitycurrency<Currencypair> implements Serializable, IUDFSupport {

  public static final String TABNAME = "currencypair";

  private static final long serialVersionUID = 1L;

  @Schema(description = "The base currency as an ISO code.")
  @Basic(optional = false)
  @NotNull
  @ValidCurrencyCode
  @Column(name = "from_currency")
  @PropertySelectiveUpdatableOrWhenNull
  private String fromCurrency;

  @Schema(description = "The quotation currency as an ISO code.")
  @Basic(optional = false)
  @NotNull
  @ValidCurrencyCode
  @Column(name = "to_currency")
  @PropertySelectiveUpdatableOrWhenNull
  private String toCurrency;

  public Currencypair() {
  }

  public Currencypair(String fromCurrency, String toCurrency) {
    this.fromCurrency = fromCurrency;
    this.toCurrency = toCurrency;
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

  public boolean getIsCryptocurrency() {
    return isFromCryptocurrency() || GlobalConstants.CRYPTO_CURRENCY_SUPPORTED.contains(toCurrency);
  }

  @JsonIgnore
  public boolean isFromCryptocurrency() {
    return GlobalConstants.CRYPTO_CURRENCY_SUPPORTED.contains(fromCurrency);
  }

  @Override
  public String getName() {
    return fromCurrency + "/" + toCurrency;
  }

  @Override
  public String toString() {
    return "Currencypair [fromCurrency=" + fromCurrency + ", toCurrency=" + toCurrency + ", idSecuritycurrency="
        + idSecuritycurrency + ", idConnectorHistory=" + idConnectorHistory + ", note=" + note + ", idConnectorIntra="
        + idConnectorIntra + ", retryHistoryLoad=" + retryHistoryLoad + ", retryIntraLoad=" + retryIntraLoad
        + ", sPrevClose=" + sPrevClose + ", sChangePercentage=" + sChangePercentage + ", sTimestamp=" + sTimestamp
        + ", sOpen=" + sOpen + ", sLast=" + sLast + "]";
  }

  @Override
  public boolean expectVolume() {
    return GlobalConstants.CRYPTO_CURRENCY_SUPPORTED.contains(fromCurrency);
  }
  
  @Override
  public boolean tenantHasAccess(Integer idTenant) {
    return true;
  }

}
