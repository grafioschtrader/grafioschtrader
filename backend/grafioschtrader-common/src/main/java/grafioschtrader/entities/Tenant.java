/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package grafioschtrader.entities;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.types.TenantKindType;
import grafioschtrader.validation.ValidCurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.NamedAttributeNode;
import jakarta.persistence.NamedEntityGraph;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = Tenant.TABNAME)
@NamedEntityGraph(name = "graph.tenant.portfolios", attributeNodes = @NamedAttributeNode("portfolioList"))
@Schema(title = "Tenant is the main access point", description = "GT defines a tenant from the aggregation of all portfolios and watchlists."
    + "Additionally, it contains the information regarding the evaluation over all portfolios.")
public class Tenant extends TenantBaseID implements Serializable {

  public static final String TABNAME = "tenant";

  private static final long serialVersionUID = 1L;
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_tenant")
  private Integer idTenant;

  @NotBlank
  @Basic(optional = false)
  @Size(min = 1, max = 25)
  @Column(name = "tenant_name")
  private String tenantName;

  @Basic(optional = false)
  @ValidCurrencyCode
  private String currency;

  @Column(name = "create_id_user")
  @Schema(description = "User ID which created this tenant, can not be set from outside")
  private Integer createIdUser;

  @Column(name = "tenant_kind_type")
  @Schema(description = "Type of tenant, can not be set from outside")
  private byte tenantKindType;

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

  @JoinColumn(name = "id_tenant")
  @OneToMany()
  private List<User> userList;

  @Column(name = "id_watchlist_performance")
  @Schema(description = "The id of the watchlist which caused a update of the tenant depend currencies")
  private Integer idWatchlistPerformance;

  public Tenant() {
  }

  public Tenant(String tenantName, String currency, Integer createIdUser, TenantKindType tenantKindType,
      boolean excludeDivTax) {
    this.tenantName = tenantName;
    this.currency = currency;
    this.createIdUser = createIdUser;
    this.tenantKindType = tenantKindType.getValue();
    this.excludeDivTax = excludeDivTax;
  }

  @JsonIgnore
  @Override
  public Integer getId() {
    return idTenant;
  }

  @Override
  public Integer getIdTenant() {
    return idTenant;
  }

  @Override
  public void setIdTenant(Integer idTenant) {
    this.idTenant = idTenant;
  }

  public String getTenantName() {
    return tenantName;
  }

  public void setTenantName(String tenantName) {
    this.tenantName = tenantName;
  }

  public Integer getCreateIdUser() {
    return createIdUser;
  }

  public void setCreateIdUser(Integer createIdUser) {
    this.createIdUser = createIdUser;
  }

  public TenantKindType getTenantKindType() {
    return TenantKindType.getTenantKindTypeByValue(tenantKindType);
  }

  public void setTenantKindType(TenantKindType tenantKindType) {
    this.tenantKindType = tenantKindType.getValue();
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
  public List<User> getUsergroupList() {
    return userList;
  }

  public void setUsergroupList(List<User> usergroupList) {
    this.userList = usergroupList;
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
    return "grafioschtrader.entities.Tenant[ idTenant=" + idTenant + " ]";
  }

}
