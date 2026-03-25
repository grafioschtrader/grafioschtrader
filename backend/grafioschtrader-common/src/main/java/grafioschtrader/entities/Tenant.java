/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package grafioschtrader.entities;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

import org.hibernate.annotations.Type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.entities.TenantBase;
import grafiosch.validation.AfterEqual;
import grafioschtrader.GlobalConstants;
import grafioschtrader.dto.TaxStatementExportRequest;
import grafioschtrader.types.TenantKindType;
import grafioschtrader.validation.ValidCurrencyCode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
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
import jakarta.validation.constraints.Size;

@Schema(description = """
    Tenant is the main access point", description = "GT defines a tenant from the aggregation of all portfolios and watchlists.
    Additionally, it contains the information regarding the evaluation over all portfolios.""")
@Entity
@Table(name = TenantBase.TABNAME)
@NamedEntityGraph(name = "graph.tenant.portfolios", attributeNodes = @NamedAttributeNode("portfolioList"))
public class Tenant extends TenantBase implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "The base currency for this tenant used for cross-portfolio evaluation and consolidated reporting.")
  @Basic(optional = false)
  @ValidCurrencyCode
  private String currency;

  @Schema(description = """
      Interest/dividend tax: In some countries, such as Switzerland, a tax amount of 35%, for example, is automatically
      deducted from certain shares and bonds when the dividend or interest is paid. This amount is refunded on the basis
      of the tax information and the income is taxed as ordinary income. If you select the skip interest/dividend tax option,
      the tax for the "Interest/dividend" transaction type is skipped when calculating the profit.
      This makes it easier to compare the profits from different securities.""")
  @Basic(optional = false)
  @NotNull
  @Column(name = "exclude_div_tax")
  private boolean excludeDivTax;

  @Schema(description = """
      Default closed-until date for all portfolios of this tenant. Transactions on or before this date are protected
      from modification. Individual portfolios can override this setting with their own closedUntil value.""")
  @Column(name = "closed_until")
  @AfterEqual(value = GlobalConstants.OLDEST_TRADING_DAY)
  private LocalDate closedUntil;

  @Schema(description = "List of portfolios belonging to this tenant, ordered alphabetically by name.")
  @JoinColumn(name = "id_tenant")
  @OneToMany()
  @OrderBy("name ASC")
  private List<Portfolio> portfolioList;

  @Schema(description = "List of watchlists associated with this tenant.")
  @JoinColumn(name = "id_tenant")
  @OneToMany()
  private List<Watchlist> watchlistList;

  @Schema(description = "The id of the watchlist which caused a update of the tenant depend currencies")
  @Column(name = "id_watchlist_performance")
  private Integer idWatchlistPerformance;

  @Schema(description = "Type of tenant, can not be set from outside")
  @Column(name = "tenant_kind_type")
  private byte tenantKindType;

  @Schema(description = "Reference to the parent tenant for simulation tenants")
  @Column(name = "id_parent_tenant")
  private Integer idParentTenant;

  @Schema(description = "Reference to the shared AlgoTop strategy for simulation tenants")
  @Column(name = "id_algo_top")
  private Integer idAlgoTop;

  @Schema(description = "ISO 3166-1 alpha-2 country code for the tenant, used to enable country-specific features such as ICTax columns.")
  @Column(name = "country")
  @Size(max = 2)
  private String country;

  @Schema(description = "Persisted tax export dialog settings (canton, institution name, client details).")
  @Type(JsonType.class)
  @Column(name = "tax_export_settings", columnDefinition = "json")
  private TaxStatementExportRequest taxExportSettings;

  @JsonCreator
  public Tenant() {
  }

  public Tenant(String tenantName, String currency, Integer createIdUser, TenantKindType tenantKindType,
      boolean excludeDivTax) {
    super(tenantName, createIdUser);
    this.tenantKindType = tenantKindType.getValue();
    this.currency = currency;
    this.excludeDivTax = excludeDivTax;
  }

  public boolean isExcludeDivTax() {
    return excludeDivTax;
  }

  public void setExcludeDivTax(boolean excludeDivTax) {
    this.excludeDivTax = excludeDivTax;
  }

  public LocalDate getClosedUntil() {
    return closedUntil;
  }

  public void setClosedUntil(LocalDate closedUntil) {
    this.closedUntil = closedUntil;
  }

  @JsonIgnore
  public List<Portfolio> getPortfolioList() {
    return portfolioList;
  }

  public void setPortfolioList(List<Portfolio> portfolioList) {
    this.portfolioList = portfolioList;
  }

  public TenantKindType getTenantKindType() {
    return TenantKindType.getTenantKindTypeByValue(tenantKindType);
  }

  public void setTenantKindType(TenantKindType tenantKindType) {
    this.tenantKindType = tenantKindType.getValue();
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

  public Integer getIdParentTenant() {
    return idParentTenant;
  }

  public void setIdParentTenant(Integer idParentTenant) {
    this.idParentTenant = idParentTenant;
  }

  public Integer getIdAlgoTop() {
    return idAlgoTop;
  }

  public void setIdAlgoTop(Integer idAlgoTop) {
    this.idAlgoTop = idAlgoTop;
  }

  public String getCountry() {
    return country;
  }

  public void setCountry(String country) {
    this.country = country;
  }

  public TaxStatementExportRequest getTaxExportSettings() {
    return taxExportSettings;
  }

  public void setTaxExportSettings(TaxStatementExportRequest taxExportSettings) {
    this.taxExportSettings = taxExportSettings;
  }

  public void updateThis(Tenant sourceTenant) {
    this.setTenantName(sourceTenant.getTenantName());
    this.setCurrency(sourceTenant.getCurrency());
    this.setExcludeDivTax(sourceTenant.isExcludeDivTax());
    this.setClosedUntil(sourceTenant.getClosedUntil());
    this.setCountry(sourceTenant.getCountry());
  }

  @Override
  public String toString() {
    return "grafioschtrader.entities.Tenant[ idTenant=" + getIdTenant() + " ]";
  }

}
