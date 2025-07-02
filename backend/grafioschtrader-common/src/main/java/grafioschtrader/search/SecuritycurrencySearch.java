package grafioschtrader.search;

import java.io.Serializable;
import java.util.Date;

import org.springframework.format.annotation.DateTimeFormat;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Defines the search criteria for securities and currency pairs.")
public class SecuritycurrencySearch implements Serializable {
  private static final long serialVersionUID = 1L;

  @Schema(description = "International Securities Identification Number (ISIN) of the security.")
  private String isin;
  
  @Schema(description = "Type of the asset class (e.g., EQUITIES, FIXED_INCOME, ...).")
  private AssetclassType assetclassType;
  
  @Schema(description = "Name of the security or currency pair.")
  private String name;
  
  @Schema(description = "ID of the stock exchange where the security is traded.")
  private Integer idStockexchange;
  
  @Schema(description = "Country code of the stock exchange.")
  private String stockexchangeCountryCode;
  
  @Schema(description = "Ticker symbol of the security.")
  private String tickerSymbol;
  
  @Schema(description = "Currency code (e.g., USD, EUR, CHF) of the security or the 'to' currency in a currency pair.")
  private String currency;

  @Schema(description = "Specific type of investment instrument (e.g., DIRECT_INVESTMENT, CFD, ...).")
  private SpecialInvestmentInstruments specialInvestmentInstruments;
  
  @Schema(description = "Localized name of the sub-category for the asset class.")
  private String subCategoryNLS;
  
  @Schema(description = """
      Indicator to search only for securities that are private for the tenant.
      True for private only, false for public or private, null for public only.""")
  private Boolean onlyTenantPrivate;
  
  @Schema(description = "Leverage factor of the security, normally only used for leveraged ETFs.")
  private Float leverageFactor;
  
  @Schema(description = "Flag to exclude derived securities (like CFDs based on an underlying stock) from the search results.")
  private boolean excludeDerivedSecurity;
  
  @Schema(description = "ID of the data connector used for historical price data. Fullname (e.g.k 'gt.datafeed.yahoo'")
  private String idConnectorHistory;
  
  @Schema(description = "ID of the data connector used for intraday price data. Fullname (e.g.k 'gt.datafeed.yahoo'")
  private String idConnectorIntra;
  
  @Schema(description = "Date to check if the security/currency is active. Format: yyyyMMdd")
  @DateTimeFormat(pattern = "yyyyMMdd")
  private Date activeDate;
 
  @Schema(description = "Flag to indicate if the search should consider instruments that the tenant has holdings in.")
  private boolean withHoldings;

  /**
   * A correlation matrix can only work with data from historical price data, so this flag is set to true.
   */
  @JsonIgnore
  private boolean noMarketValue;

  /**
   * A correlation matrix must have historical price data for a specific time period. This date defines the start date.
   */
  @JsonIgnore
  private Date maxFromDate;

  /**
   * A correlation matrix must have historical price data for a specific time period. This date defines the end date.
   */
  @JsonIgnore
  private Date minToDate;

  public String getIsin() {
    return isin;
  }

  public void setIsin(String isin) {
    this.isin = isin;
  }

  public String getName() {
    return name;
  }

  public String getSubCategoryNLS() {
    return subCategoryNLS;
  }

  public void setSubCategoryNLS(String subCategoryNLS) {
    this.subCategoryNLS = subCategoryNLS;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Integer getIdStockexchange() {
    return idStockexchange;
  }

  public void setIdStockexchange(Integer idStockexchange) {
    this.idStockexchange = idStockexchange;
  }

  public String getStockexchangeCountryCode() {
    return stockexchangeCountryCode;
  }

  public void setStockexchangeCountryCode(String stockexchangeCountryCode) {
    this.stockexchangeCountryCode = stockexchangeCountryCode;
  }

  public String getTickerSymbol() {
    return tickerSymbol;
  }

  public void setTickerSymbol(String tickerSymbol) {
    this.tickerSymbol = tickerSymbol;
  }

  public AssetclassType getAssetclassType() {
    return assetclassType;
  }

  public void setAssetclassType(AssetclassType assetclassType) {
    this.assetclassType = assetclassType;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public SpecialInvestmentInstruments getSpecialInvestmentInstruments() {
    return specialInvestmentInstruments;
  }

  public void setSpecialInvestmentInstruments(SpecialInvestmentInstruments specialInvestmentInstruments) {
    this.specialInvestmentInstruments = specialInvestmentInstruments;
  }

  public Boolean isOnlyTenantPrivate() {
    return onlyTenantPrivate;
  }

  public void setOnlyTenantPrivate(Boolean onlyTenantPrivate) {
    this.onlyTenantPrivate = onlyTenantPrivate;
  }

  public Date getActiveDate() {
    return activeDate;
  }

  public void setActiveDate(Date activeDate) {
    this.activeDate = activeDate;
  }

  public Float getLeverageFactor() {
    return leverageFactor;
  }

  public void setLeverageFactor(Float leverageFactor) {
    this.leverageFactor = leverageFactor;
  }

  public String getIdConnectorHistory() {
    return idConnectorHistory;
  }

  public void setIdConnectorHistory(String idConnectorHistory) {
    this.idConnectorHistory = idConnectorHistory;
  }

  public String getIdConnectorIntra() {
    return idConnectorIntra;
  }

  public void setIdConnectorIntra(String idConnectorIntra) {
    this.idConnectorIntra = idConnectorIntra;
  }

  public boolean isExcludeDerivedSecurity() {
    return excludeDerivedSecurity;
  }

  public void setExcludeDerivedSecurity(boolean excludeDerivedSecurity) {
    this.excludeDerivedSecurity = excludeDerivedSecurity;
  }

  public boolean getWithHoldings() {
    return withHoldings;
  }

  public void setWithHoldings(boolean withHoldings) {
    this.withHoldings = withHoldings;
  }

  public boolean isNoMarketValue() {
    return noMarketValue;
  }

  public void setNoMarketValue(boolean noMarketValue) {
    this.noMarketValue = noMarketValue;
  }

  public Date getMaxFromDate() {
    return maxFromDate;
  }

  public void setMaxFromDate(Date maxFromDate) {
    this.maxFromDate = maxFromDate;
  }

  public Date getMinToDate() {
    return minToDate;
  }

  public void setMinToDate(Date minToDate) {
    this.minToDate = minToDate;
  }

  @Override
  public String toString() {
    return "SecuritycurrencySearch [isin=" + isin + ", name=" + name + ", tickerSymbol=" + tickerSymbol + ", currency="
        + currency + ", assetclassType=" + assetclassType + ", specialInvestmentInstruments="
        + specialInvestmentInstruments + ", subCategoryNLS=" + subCategoryNLS + ", onlyTenantPrivate="
        + onlyTenantPrivate + ", leverageFactor=" + leverageFactor + ", activeDate=" + activeDate + "]";
  }

}
