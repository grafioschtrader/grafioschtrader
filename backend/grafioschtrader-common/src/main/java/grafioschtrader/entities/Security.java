/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package grafioschtrader.entities;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.PropertyAlwaysUpdatable;
import grafioschtrader.common.PropertyOnlyCreation;
import grafioschtrader.common.PropertySelectiveUpdatableOrWhenNull;
import grafioschtrader.entities.projection.IFormulaInSecurity;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.DistributionFrequency;
import grafioschtrader.types.SpecialInvestmentInstruments;
import grafioschtrader.validation.AfterEqual;
import grafioschtrader.validation.DateRange;
import grafioschtrader.validation.NonZeroFloatConstraint;
import grafioschtrader.validation.ValidCurrencyCode;
import grafioschtrader.validation.ValidISIN;
import grafioschtrader.validation.WebUrl;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedAttributeNode;
import jakarta.persistence.NamedEntityGraph;
import jakarta.persistence.NamedStoredProcedureQuery;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = Security.TABNAME)
@DiscriminatorValue("S")
@DateRange(start = "activeFromDate", end = "activeToDate")
@NamedStoredProcedureQuery(name = "Security.deleteUpdateHistoryQuality", procedureName = "deleteUpdateHistoryQuality ")
@NamedEntityGraph(name = "graph.security.historyquote", attributeNodes = { @NamedAttributeNode("historyquoteList"),
    @NamedAttributeNode("assetClass"), @NamedAttributeNode("stockexchange") })
@Schema(description = "Contains the characteristics of a security that may be traded")
public class Security extends Securitycurrency<Security> implements Serializable, IFormulaInSecurity {

  public static final String TABNAME = "security";

  public static final String SPLIT_ARRAY = "splitPropose";
  public static final String HISTORYQUOTE_PERIOD_ARRAY = "hpPropose";

  private static final long serialVersionUID = 1L;

  @Schema(description = "Currency of security, ISO 4217")
  @ValidCurrencyCode
  @Column(name = "currency")
  @PropertySelectiveUpdatableOrWhenNull
  private String currency;

  @Schema(description = "International Securities Identification Number, ISO 6166")
  @ValidISIN
  @Column(name = "isin")
  @PropertySelectiveUpdatableOrWhenNull
  private String isin;

  @Schema(description = "Public traded security has a ticker symbol which is a unique abbreviation")
  @Column(name = "ticker_symbol")
  @PropertyAlwaysUpdatable
  private String tickerSymbol;

  @Column(name = "s_volume")
  private Long sVolume;

  @Basic(optional = false)
  @NotNull
  @Size(min = 2, max = 80)
  @PropertyAlwaysUpdatable
  private String name;

  @JoinColumn(name = "id_asset_class", referencedColumnName = "id_asset_class")
  @ManyToOne(optional = false, fetch = FetchType.EAGER)
  @PropertyAlwaysUpdatable
  private Assetclass assetClass;

  @Column(name = "denomination")
  @PropertyAlwaysUpdatable
  private Integer denomination;

  @JoinColumn(name = "id_stockexchange", referencedColumnName = "id_stockexchange")
  @ManyToOne(optional = false, fetch = FetchType.EAGER)
  @PropertyAlwaysUpdatable
  @NotNull
  private Stockexchange stockexchange;

  @Schema(description = "HTML link to the product description")
  @Column(name = "product_link")
  @WebUrl
  @PropertyAlwaysUpdatable
  private String productLink;

  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  @Column(name = "active_from_date")
  @Temporal(TemporalType.DATE)
  @PropertyAlwaysUpdatable
  @NotNull
  @AfterEqual(value = GlobalConstants.OLDEST_TRADING_DAY, format = GlobalConstants.STANDARD_DATE_FORMAT)
  private Date activeFromDate;

  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  @Column(name = "active_to_date")
  @Temporal(TemporalType.DATE)
  @PropertyAlwaysUpdatable
  @NotNull
  @AfterEqual(value = GlobalConstants.OLDEST_TRADING_DAY, format = GlobalConstants.STANDARD_DATE_FORMAT)
  private Date activeToDate;

  @Schema(description = "Some security pays dividend or interest in a certain frequency. "
      + "It is used for transaction import of bonds to check interest amount against coupon rate")
  @Column(name = "dist_frequency")
  @PropertyAlwaysUpdatable
  private byte distributionFrequency;

  @Schema(description = "Certain instruments are short and may still be leveraged, this is mapped with this."
      + "Used, for example, to correctly calculate the equity ratio for the portfolio.")
  @Column(name = "leverage_factor")
  @PropertyAlwaysUpdatable
  @DecimalMin("-9.99")
  @DecimalMax("9.99")
  @NonZeroFloatConstraint
  private float leverageFactor;

  @Schema(description = "Contains Id of tenant if it is a private security which belongs to this tenant")
  @Column(name = "id_tenant_private")
  @PropertyOnlyCreation
  private Integer idTenantPrivate;

  @Schema(description = "Used for derived security, which depends on other security")
  @Size(min = 1, max = 255)
  @Column(name = "formula_prices")
  @PropertyAlwaysUpdatable
  private String formulaPrices;

  @Column(name = "id_link_securitycurrency")
  @PropertyAlwaysUpdatable
  private Integer idLinkSecuritycurrency;

  @Column(name = "id_connector_dividend")
  @PropertyAlwaysUpdatable
  protected String idConnectorDividend;

  @Column(name = "url_dividend_extend")
  @Size(min = 1, max = 254)
  @PropertyAlwaysUpdatable
  private String urlDividendExtend;

  @Schema(description = "Currency of the dividend when different of the security currency, ISO 4217")
  @ValidCurrencyCode(optional = true)
  @PropertyAlwaysUpdatable
  @Column(name = "dividend_currency")
  private String dividendCurrency;

  @Column(name = "retry_dividend_load")
  @PropertyAlwaysUpdatable
  private Short retryDividendLoad = 0;

  @Column(name = "id_connector_split")
  @PropertyAlwaysUpdatable
  protected String idConnectorSplit;

  @Column(name = "url_split_extend ")
  @Size(min = 1, max = 254)
  @PropertyAlwaysUpdatable
  private String urlSplitExtend;

  @Column(name = "retry_split_load")
  @PropertyAlwaysUpdatable
  private Short retrySplitLoad = 0;
  
  @Column(name = "div_earliest_next_check")
  @Temporal(TemporalType.TIMESTAMP)
  private Date dividendEarliestNextCheck;

  @Transient
  @PropertyAlwaysUpdatable
  private SecurityDerivedLink securityDerivedLinks[];

  /**
   * It is only used for propose changes
   */
  @Transient
  private Securitysplit[] splitPropose;

  @Transient
  private HistoryquotePeriod[] hpPropose;

  /**
   * Price for history quote period
   */
  @Transient
  private Double price;

  public Security() {
    super();
  }

  public Security(String name, String currency, Assetclass assetClass, Stockexchange stockexchange, Date activeFromDate,
      Date activeToDate, DistributionFrequency distributionFrequency, String tickerSymbol, String isin) {
    this.name = name;
    this.currency = currency;
    this.assetClass = assetClass;
    this.stockexchange = stockexchange;
    this.activeFromDate = activeFromDate;
    this.activeToDate = activeToDate;
    this.distributionFrequency = distributionFrequency.getValue();
    this.tickerSymbol = tickerSymbol;
    this.isin = isin;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public String getIsin() {
    return isin;
  }

  public void setIsin(String isin) {
    this.isin = isin;
  }

  public String getTickerSymbol() {
    return tickerSymbol;
  }

  public void setTickerSymbol(String tickerSymbol) {
    this.tickerSymbol = tickerSymbol;
  }

  @JsonProperty("sVolume")
  public Long getSVolume() {
    return sVolume;
  }

  public void setSVolume(Long sVolume) {
    this.sVolume = sVolume;
  }

  @Override
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Assetclass getAssetClass() {
    return assetClass;
  }

  public void setAssetClass(Assetclass assetClass) {
    this.assetClass = assetClass;
  }

  public Stockexchange getStockexchange() {
    return stockexchange;
  }

  public void setStockexchange(Stockexchange stockexchange) {
    this.stockexchange = stockexchange;
  }

  public Date getActiveFromDate() {
    return activeFromDate;
  }

  public void setActiveFromDate(Date activeFromDate) {
    this.activeFromDate = activeFromDate;
  }

  public Date getActiveToDate() {
    return activeToDate;
  }

  public void setActiveToDate(Date activeToDate) {
    this.activeToDate = activeToDate;
  }

  public DistributionFrequency getDistributionFrequency() {
    return DistributionFrequency.getDistributionFrequency(distributionFrequency);
  }

  public void setDistributionFrequency(DistributionFrequency distributionFrequency) {
    this.distributionFrequency = distributionFrequency.getValue();
  }

  public float getLeverageFactor() {
    return leverageFactor;
  }

  public void setLeverageFactor(float leverageFactor) {
    this.leverageFactor = leverageFactor;
  }

  public String getProductLink() {
    return productLink;
  }

  public Integer getDenomination() {
    return denomination;
  }

  public void setDenomination(Integer denomination) {
    this.denomination = denomination;
  }

  public void setProductLink(String productLink) {
    this.productLink = productLink;
  }

  public Integer getIdTenantPrivate() {
    return idTenantPrivate;
  }

  public void setIdTenantPrivate(Integer idTenantPrivate) {
    this.idTenantPrivate = idTenantPrivate;
  }

  public Securitysplit[] getSplitPropose() {
    return splitPropose;
  }

  public void setSplitPropose(Securitysplit[] splitPropose) {
    this.splitPropose = splitPropose;
  }

  public HistoryquotePeriod[] getHpPropose() {
    return hpPropose;
  }

  public void setHpPropose(HistoryquotePeriod[] hpPropose) {
    this.hpPropose = hpPropose;
  }

  public Double getPrice() {
    return price;
  }

  public void setPrice(Double price) {
    this.price = price;
  }

  @Override
  public String getFormulaPrices() {
    return formulaPrices;
  }

  public void setFormulaPrices(String formulaPrices) {
    this.formulaPrices = formulaPrices;
  }

  @Override
  public boolean isCalculatedPrice() {
    return formulaPrices != null;
  }

  @JsonIgnore
  public boolean isStockAndDirectInvestment() {
    return getAssetClass().getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.DIRECT_INVESTMENT
        && getAssetClass().getCategoryType() == AssetclassType.EQUITIES;
  }

  @JsonIgnore
  public boolean isMarginInstrument() {
    return getAssetClass().getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.CFD
        || getAssetClass().getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.FOREX;
  }

  @JsonIgnore
  public boolean isDerivedInstrument() {
    return idLinkSecuritycurrency != null;
  }

  @Override
  public Integer getIdLinkSecuritycurrency() {
    return idLinkSecuritycurrency;
  }

  public void setIdLinkSecuritycurrency(Integer idLinkSecuritycurrency) {
    this.idLinkSecuritycurrency = idLinkSecuritycurrency;
  }

  public SecurityDerivedLink[] getSecurityDerivedLinks() {
    return securityDerivedLinks;
  }

  public void setSecurityDerivedLinks(SecurityDerivedLink[] securityDerivedLinks) {
    this.securityDerivedLinks = securityDerivedLinks;
  }

  public String getIdConnectorDividend() {
    return idConnectorDividend;
  }

  public void setIdConnectorDividend(String idConnectorDividend) {
    this.idConnectorDividend = idConnectorDividend;
  }

  public String getUrlDividendExtend() {
    return urlDividendExtend;
  }

  public void setUrlDividendExtend(String urlDividendExtend) {
    this.urlDividendExtend = urlDividendExtend;
  }

  public String getDividendCurrency() {
    return dividendCurrency;
  }

  public void setDividendCurrency(String dividendCurrency) {
    this.dividendCurrency = dividendCurrency;
  }

  public Short getRetryDividendLoad() {
    return retryDividendLoad;
  }

  public void setRetryDividendLoad(Short retryDividendLoad) {
    this.retryDividendLoad = retryDividendLoad;
  }

  public String getIdConnectorSplit() {
    return idConnectorSplit;
  }

  public void setIdConnectorSplit(String idConnectorSplit) {
    this.idConnectorSplit = idConnectorSplit;
  }

  public String getUrlSplitExtend() {
    return urlSplitExtend;
  }

  public void setUrlSplitExtend(String urlSplitExtend) {
    this.urlSplitExtend = urlSplitExtend;
  }

  public Short getRetrySplitLoad() {
    return retrySplitLoad;
  }

  public void setRetrySplitLoad(Short retrySplitLoad) {
    this.retrySplitLoad = retrySplitLoad;
  }
 

  public Date getDividendEarliestNextCheck() {
    return dividendEarliestNextCheck;
  }

  public void setDividendEarliestNextCheck(Date dividendEarliestNextCheck) {
    this.dividendEarliestNextCheck = dividendEarliestNextCheck;
  }

  @Override
  public boolean isActiveForIntradayUpdate(Date now) {
    return !now.after(getActiveToDate());
  }

  @JsonIgnore
  public boolean canHaveDividendConnector() {
    if (isDerivedInstrument()) {
      return false;
    }
    boolean canHaveDividend = false;
    if (distributionFrequency != DistributionFrequency.DF_NONE.getValue()) {
      canHaveDividend = this.assetClass.getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.ETF
          || this.assetClass.getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.PENSION_FUNDS
          || this.assetClass.getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.MUTUAL_FUND;
      switch (this.assetClass.getCategoryType()) {
      case EQUITIES:
      case REAL_ESTATE:
        canHaveDividend = canHaveDividend
            || this.assetClass.getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.DIRECT_INVESTMENT;
        break;
      case COMMODITIES:
        canHaveDividend = this.assetClass.getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.ETF;
        break;
      case CREDIT_DERIVATIVE:
      case CURRENCY_PAIR:
        canHaveDividend = false;
        break;
      default:
        // Do nothing
        break;
      }
    }
    return canHaveDividend;
  }

  public void clearProperties() {
    if (!this.canHaveSplitConnector()) {
      this.idConnectorSplit = null;
      this.urlSplitExtend = null;
      this.retrySplitLoad = 0;
    }
    if (!this.canHaveDividendConnector()) {
      this.idConnectorDividend = null;
      this.urlDividendExtend = null;
      this.retryDividendLoad = 0;
    }
    if (this.stockexchange.isNoMarketValue() || leverageFactor == 0f) {
      this.leverageFactor = 1;
    }
  }

  @Override
  public void clearUnusedFields() {
    super.clearUnusedFields();
    if (isMarginInstrument()) {
      isin = null;
    }
    if (idTenantPrivate != null) {
      isin = null;
      tickerSymbol = null;
    }
  }

  @JsonIgnore
  public boolean canHaveSplitConnector() {
    return isDerivedInstrument() ? false
        : !((this.assetClass.getCategoryType() == AssetclassType.CONVERTIBLE_BOND
            || this.assetClass.getCategoryType() == AssetclassType.FIXED_INCOME)
            && this.assetClass.getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.DIRECT_INVESTMENT)
            && (this.assetClass.getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.ETF
                || this.assetClass.getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.PENSION_FUNDS
                || this.assetClass.getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.MUTUAL_FUND
                || this.assetClass.getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.CFD
                || this.assetClass.getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.DIRECT_INVESTMENT);
  }

  @JsonIgnore
  public Double getSecurityTransImportDistributionFrequency() {
    Byte frequency = null;
    if ((assetClass.getCategoryType() == AssetclassType.FIXED_INCOME
        || assetClass.getCategoryType() == AssetclassType.CONVERTIBLE_BOND)
        && assetClass.getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.DIRECT_INVESTMENT) {
      frequency = getDistributionFrequency().getDistributionFrequencyAsNumberWhenFrequency();

    }
    return frequency == null ? null : frequency.doubleValue();
  }

  @Override
  public String toString() {
    return "Security [currency=" + currency + ", isin=" + isin + ", tickerSymbol=" + tickerSymbol + ", sVolume="
        + sVolume + ", name=" + name + ", assetClass=" + assetClass + ", denomination=" + denomination
        + ", stockexchange=" + stockexchange + ", productLink=" + productLink + ", activeFromDate=" + activeFromDate
        + ", activeToDate=" + activeToDate + ", distributionFrequency=" + distributionFrequency + ", leverageFactor="
        + leverageFactor + ", idTenantPrivate=" + idTenantPrivate + ", formulaPrices=" + formulaPrices
        + ", idLinkSecuritycurrency=" + idLinkSecuritycurrency + ", securityDerivedLinks="
        + Arrays.toString(securityDerivedLinks) + ", splitPropose=" + Arrays.toString(splitPropose) + ", hpPropose="
        + Arrays.toString(hpPropose) + ", price=" + price + ", idSecuritycurrency=" + idSecuritycurrency
        + ", sPrevClose=" + sPrevClose + ", sChangePercentage=" + sChangePercentage + ", sTimestamp=" + sTimestamp
        + ", sOpen=" + sOpen + ", sLast=" + sLast + ", sLow=" + sLow + ", sHigh=" + sHigh + "]";
  }

  @Override
  public boolean exspectVolume() {
    return !(isDerivedInstrument()
        || this.assetClass.getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.NON_INVESTABLE_INDICES);
  }

}
