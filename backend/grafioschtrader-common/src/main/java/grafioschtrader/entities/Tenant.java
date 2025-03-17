/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package grafioschtrader.entities;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.entities.TenantBase;
import grafiosch.types.TenantKindType;
import grafioschtrader.validation.ValidCurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.NamedAttributeNode;
import jakarta.persistence.NamedEntityGraph;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = Tenant.TABNAME)
@NamedEntityGraph(name = "graph.tenant.portfolios", attributeNodes = @NamedAttributeNode("portfolioList"))
@Schema(title = "Tenant is the main access point", description = "GT defines a tenant from the aggregation of all portfolios and watchlists."
    + "Additionally, it contains the information regarding the evaluation over all portfolios.")
public class Tenant extends TenantBase implements Serializable {
  
  public static final String TABNAME = "tenant";
  private static final long serialVersionUID = 1L;
  
  @Basic(optional = false)
  @ValidCurrencyCode
  private String currency;
 
  @Basic(optional = false)
  @NotNull
  @Column(name = "exclude_div_tax")
  private boolean excludeDivTax;

  @JoinColumn(name = "id_tenant")
  @OneToMany()
  @OrderBy("name ASC")
  private List<Portfolio> portfolioList;

  @JoinColumn(name = "id_tenant")
  @OneToMany()
  private List<Watchlist> watchlistList;

  
  @Column(name = "id_watchlist_performance")
  @Schema(description = "The id of the watchlist which caused a update of the tenant depend currencies")
  private Integer idWatchlistPerformance;

  public Tenant() {
  }

  public Tenant(String tenantName, String currency, Integer createIdUser, TenantKindType tenantKindType,
      boolean excludeDivTax) {
    super(tenantName, createIdUser, tenantKindType);
    this.currency = currency;
    this.excludeDivTax = excludeDivTax;
  }
 
  public boolean isExcludeDivTax() {
    return excludeDivTax;
  }

  public void setExcludeDivTax(boolean excludeDivTax) {
    this.excludeDivTax = excludeDivTax;
  }

  @JsonIgnore
  public List<Portfolio> getPortfolioList() {
    return portfolioList;
  }

  public void setPortfolioList(List<Portfolio> portfolioList) {
    this.portfolioList = portfolioList;
  }
 
  @JsonIgnore
  public List<Watchlist> getWatchlistList() {
    return watchlistList;
  }

  public void setWatchlistList(List<Watchlist> watchlistList) {
    this.watchlistList = watchlistList;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public Integer getIdWatchlistPerformance() {
    return idWatchlistPerformance;
  }

  public void setIdWatchlistPerformance(Integer idWatchlistPerformance) {
    this.idWatchlistPerformance = idWatchlistPerformance;
  }

  public void updateThis(Tenant sourceTenant) {
    this.setTenantName(sourceTenant.getTenantName());
    this.setCurrency(sourceTenant.getCurrency());
    this.setExcludeDivTax(sourceTenant.isExcludeDivTax());
  }

  @Override
  public String toString() {
    return "grafioschtrader.entities.Tenant[ idTenant=" + getIdTenant() + " ]";
  }

}
