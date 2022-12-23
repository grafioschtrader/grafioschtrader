/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package grafioschtrader.entities;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.PropertySelectiveUpdatableOrWhenNull;
import grafioschtrader.validation.ValidCurrencyCode;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.NamedAttributeNode;
import jakarta.persistence.NamedEntityGraph;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

/**
 *
 * @author Hugo Graf
 */
@Entity
@Table(name = Currencypair.TABNAME)
@DiscriminatorValue("C")
@NamedEntityGraph(name = "graph.currency.historyquote", attributeNodes = @NamedAttributeNode("historyquoteList"))
public class Currencypair extends Securitycurrency<Currencypair> implements Serializable {

  public static final String TABNAME = "currencypair";

  private static final long serialVersionUID = 1L;

  @Basic(optional = false)
  @NotNull
  @ValidCurrencyCode
  @Column(name = "from_currency")
  @PropertySelectiveUpdatableOrWhenNull
  private String fromCurrency;

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
  public boolean exspectVolume() {
    return GlobalConstants.CRYPTO_CURRENCY_SUPPORTED.contains(fromCurrency);
  }

}
