/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package grafioschtrader.entities;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.common.PropertyAlwaysUpdatable;
import grafioschtrader.validation.ValidCurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


@Entity
@Table(name = Portfolio.TABNAME)
@Schema(description = "Entity containing the information of a portfolio")
public class Portfolio extends TenantBaseID implements Serializable {

  public static final String TABNAME = "portfolio";
  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_portfolio")
  private Integer idPortfolio;

  @Schema(description = "Name of portfolio, it is unique for a tenant")
  @Basic(optional = false)
  @NotBlank
  @Size(min = 1, max = 25)
  @PropertyAlwaysUpdatable
  private String name;

  @Schema(description = "Currency of security, ISO 4217")
  @Basic(optional = false)
  @ValidCurrencyCode
  @PropertyAlwaysUpdatable
  private String currency;

  @Column(name = "id_tenant")
  private Integer idTenant;

  @OneToMany(mappedBy = "portfolio", orphanRemoval = true, fetch = FetchType.EAGER)
  private List<Securitycashaccount> securitycashaccountList;

  public Portfolio() {
  }

  public Portfolio(Integer idTenant, String name, String currency) {
    this.idTenant = idTenant;
    this.name = name;
    this.currency = currency;
  }

  @JsonIgnore
  @Override
  public Integer getId() {
    return idPortfolio;
  }

  public Integer getIdPortfolio() {
    return idPortfolio;
  }

  public void setIdPortfolio(Integer idPortfolio) {
    this.idPortfolio = idPortfolio;
  }

  public String getName() {
    return name;
  }

  @Override
  public Integer getIdTenant() {
    return idTenant;
  }

  @Override
  public void setIdTenant(Integer idTenant) {
    this.idTenant = idTenant;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  @JsonIgnore
  public List<Securitycashaccount> getSecuritycashaccountList() {
    return securitycashaccountList;
  }

  public void setSecuritycashaccountList(List<Securitycashaccount> securitycashaccountList) {
    this.securitycashaccountList = securitycashaccountList;
  }

  @SuppressWarnings("unused")
  private void setSecurityaccountList(List<Securityaccount> empty) {
  }

  public List<Securityaccount> getSecurityaccountList() {
    return (securitycashaccountList != null)
        ? this.securitycashaccountList.stream()
            .filter(securitycashaccount -> securitycashaccount instanceof Securityaccount)
            .map(securitycashaccount -> (Securityaccount) securitycashaccount).collect(Collectors.toList())
        : null;
  }

  public void setCashaccountList(List<Cashaccount> empty) {
  }

  public List<Cashaccount> getCashaccountList() {
    return (securitycashaccountList != null)
        ? this.securitycashaccountList.stream()
            .filter(securitycashaccount -> securitycashaccount instanceof Cashaccount)
            .map(securitycashaccount -> (Cashaccount) securitycashaccount).collect(Collectors.toList())
        : null;
  }

  public static Cashaccount getPreferedCashaccountForPorfolioBySecurityAccountAndCurrency(
      List<Cashaccount> cashaccountList, Integer idSecurityaccount, String currency) {
    return getPreferedCashaccountBySecurityAccountAndCurrency(cashaccountList, idSecurityaccount, currency);
  }

  private static Cashaccount getPreferedCashaccountBySecurityAccountAndCurrency(List<Cashaccount> cashaccountList,
      Integer idSecurityaccount, String currency) {
    for (Cashaccount cashaccount : cashaccountList) {
      if (cashaccount.getCurrency().equals(currency)) {
        if (cashaccount.getConnectIdSecurityaccount() == null || (cashaccount.getConnectIdSecurityaccount() != null
            && cashaccount.getConnectIdSecurityaccount().equals(idSecurityaccount))) {
          return cashaccount;
        }
      }
    }
    return null;
  }

  @Override
  public String toString() {
    return "Portfolio{" + "idPortfolio=" + idPortfolio + ", name=" + name + ", currency=" + currency
        + ", securitycashaccountList=" + securitycashaccountList + '}';
  }

}
