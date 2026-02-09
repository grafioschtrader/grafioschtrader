package grafioschtrader.gtnet.model;

import java.util.Date;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.DistributionFrequency;
import grafioschtrader.types.SpecialInvestmentInstruments;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for security metadata received from GTNet peers. Contains instance-agnostic data that can be used
 * across different GT installations. Excludes local IDs and API keys which are instance-specific.
 */
@Schema(description = """
    Security metadata DTO for GTNet peer exchange. Contains instance-agnostic data including identification,
    asset classification, stock exchange info, and connector hints. Excludes local database IDs and API keys
    which are instance-specific.""")
public class SecurityGtnetLookupDTO {

  // Identification
  @Schema(description = "ISIN (International Securities Identification Number)")
  private String isin;

  @Schema(description = "ISO 4217 currency code")
  private String currency;

  @Schema(description = "Security name")
  private String name;

  @Schema(description = "Ticker symbol")
  private String tickerSymbol;

  // Asset class (enum values, not local IDs)
  @Schema(description = "Asset class category type (EQUITIES, FIXED_INCOME, etc.)")
  private AssetclassType categoryType;

  @Schema(description = "Special investment instrument type (ETF, STOCK, BOND, etc.)")
  private SpecialInvestmentInstruments specialInvestmentInstrument;

  @Schema(description = "Sub-category of asset class in multiple languages (e.g., 'Emerging Markets', 'US Large Cap')")
  private Map<String, String> subCategoryNLS;

  @Schema(description = "Detected categorization scheme: REGIONAL (geographical) or SECTOR (industry)")
  private SubCategoryScheme subCategoryScheme;

  // Stock exchange (MIC code for cross-instance mapping)
  @Schema(description = "ISO 10383 Market Identifier Code of the stock exchange")
  private String stockexchangeMic;

  @Schema(description = "Stock exchange name as fallback if MIC is not available")
  private String stockexchangeName;

  @Schema(description = "URL link to the security on the stock exchange website")
  private String stockexchangeLink;

  // Connector hints (no API keys exposed)
  @Schema(description = "Hints about which connectors work for this security")
  private List<ConnectorHint> connectorHints;

  // Security properties
  @Schema(description = "Bond denomination value")
  private Integer denomination;

  @Schema(description = "Dividend/interest distribution frequency")
  private DistributionFrequency distributionFrequency;

  @Schema(description = "Leverage factor for leveraged products")
  private Float leverageFactor;

  @Schema(description = "URL to product information page")
  private String productLink;

  @Schema(description = "Date from which the security is actively traded")
  private Date activeFromDate;

  @Schema(description = "Date until which the security is actively traded")
  private Date activeToDate;

  // Connector retry counters
  @Schema(description = "Retry counter for historical price data loading")
  private Short retryHistoryLoad;

  @Schema(description = "Retry counter for intraday price data loading")
  private Short retryIntraLoad;

  @Schema(description = "Retry counter for dividend data loading")
  private Short retryDividendLoad;

  @Schema(description = "Retry counter for split data loading")
  private Short retrySplitLoad;

  // Intraday timestamp
  @Schema(description = "Timestamp of the last intraday price update")
  @JsonProperty("sTimestamp")
  private Date sTimestamp;

  // History quality data (from historyquote_quality table)
  @Schema(description = "Earliest available historical quote date (YYYY-MM-DD)")
  private String historyMinDate;

  @Schema(description = "Latest available historical quote date (YYYY-MM-DD)")
  private String historyMaxDate;

  @Schema(description = "Percentage of quotes with valid open, high, and low values (0-100)")
  private Double ohlPercentage;

  // Dividend and split counts
  @Schema(description = "Number of dividend records for this security")
  private Integer dividendCount;

  @Schema(description = "Number of split records for this security")
  private Integer splitCount;

  // Source tracking
  @Schema(description = "Domain name of the GTNet peer that provided this data")
  private String sourceDomain;

  // Matched connector fields (populated by receiving instance after matching against local connectors)
  @Schema(description = "Score indicating how well connectors match local configuration (higher is better)")
  private Integer connectorMatchScore;

  @Schema(description = "Matched local connector ID for historical price data")
  private String matchedHistoryConnector;

  @Schema(description = "URL extension for the matched history connector")
  private String matchedHistoryUrlExtension;

  @Schema(description = "Matched local connector ID for intraday price data")
  private String matchedIntraConnector;

  @Schema(description = "URL extension for the matched intraday connector")
  private String matchedIntraUrlExtension;

  @Schema(description = "Matched local connector ID for dividend data")
  private String matchedDividendConnector;

  @Schema(description = "URL extension for the matched dividend connector")
  private String matchedDividendUrlExtension;

  @Schema(description = "Matched local connector ID for split data")
  private String matchedSplitConnector;

  @Schema(description = "URL extension for the matched split connector")
  private String matchedSplitUrlExtension;

  @Schema(description = "Matched local asset class ID based on categoryType, specialInvestmentInstrument, and subCategoryNLS")
  private Integer matchedAssetClassId;

  @Schema(description = "Asset class match type: EXACT (with subCategoryNLS), PARTIAL (categoryType + specialInvestmentInstrument only), SCHEME_MATCH (same categorization scheme)")
  private String assetClassMatchType;

  public SecurityGtnetLookupDTO() {
  }

  // Getters and setters

  public String getIsin() {
    return isin;
  }

  public void setIsin(String isin) {
    this.isin = isin;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getTickerSymbol() {
    return tickerSymbol;
  }

  public void setTickerSymbol(String tickerSymbol) {
    this.tickerSymbol = tickerSymbol;
  }

  public AssetclassType getCategoryType() {
    return categoryType;
  }

  public void setCategoryType(AssetclassType categoryType) {
    this.categoryType = categoryType;
  }

  public SpecialInvestmentInstruments getSpecialInvestmentInstrument() {
    return specialInvestmentInstrument;
  }

  public void setSpecialInvestmentInstrument(SpecialInvestmentInstruments specialInvestmentInstrument) {
    this.specialInvestmentInstrument = specialInvestmentInstrument;
  }

  public Map<String, String> getSubCategoryNLS() {
    return subCategoryNLS;
  }

  public void setSubCategoryNLS(Map<String, String> subCategoryNLS) {
    this.subCategoryNLS = subCategoryNLS;
  }

  public SubCategoryScheme getSubCategoryScheme() {
    return subCategoryScheme;
  }

  public void setSubCategoryScheme(SubCategoryScheme subCategoryScheme) {
    this.subCategoryScheme = subCategoryScheme;
  }

  public String getStockexchangeMic() {
    return stockexchangeMic;
  }

  public void setStockexchangeMic(String stockexchangeMic) {
    this.stockexchangeMic = stockexchangeMic;
  }

  public String getStockexchangeName() {
    return stockexchangeName;
  }

  public void setStockexchangeName(String stockexchangeName) {
    this.stockexchangeName = stockexchangeName;
  }

  public String getStockexchangeLink() {
    return stockexchangeLink;
  }

  public void setStockexchangeLink(String stockexchangeLink) {
    this.stockexchangeLink = stockexchangeLink;
  }

  public List<ConnectorHint> getConnectorHints() {
    return connectorHints;
  }

  public void setConnectorHints(List<ConnectorHint> connectorHints) {
    this.connectorHints = connectorHints;
  }

  public Integer getDenomination() {
    return denomination;
  }

  public void setDenomination(Integer denomination) {
    this.denomination = denomination;
  }

  public DistributionFrequency getDistributionFrequency() {
    return distributionFrequency;
  }

  public void setDistributionFrequency(DistributionFrequency distributionFrequency) {
    this.distributionFrequency = distributionFrequency;
  }

  public Float getLeverageFactor() {
    return leverageFactor;
  }

  public void setLeverageFactor(Float leverageFactor) {
    this.leverageFactor = leverageFactor;
  }

  public String getProductLink() {
    return productLink;
  }

  public void setProductLink(String productLink) {
    this.productLink = productLink;
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

  public String getSourceDomain() {
    return sourceDomain;
  }

  public void setSourceDomain(String sourceDomain) {
    this.sourceDomain = sourceDomain;
  }

  public Integer getConnectorMatchScore() {
    return connectorMatchScore;
  }

  public void setConnectorMatchScore(Integer connectorMatchScore) {
    this.connectorMatchScore = connectorMatchScore;
  }

  public String getMatchedHistoryConnector() {
    return matchedHistoryConnector;
  }

  public void setMatchedHistoryConnector(String matchedHistoryConnector) {
    this.matchedHistoryConnector = matchedHistoryConnector;
  }

  public String getMatchedHistoryUrlExtension() {
    return matchedHistoryUrlExtension;
  }

  public void setMatchedHistoryUrlExtension(String matchedHistoryUrlExtension) {
    this.matchedHistoryUrlExtension = matchedHistoryUrlExtension;
  }

  public String getMatchedIntraConnector() {
    return matchedIntraConnector;
  }

  public void setMatchedIntraConnector(String matchedIntraConnector) {
    this.matchedIntraConnector = matchedIntraConnector;
  }

  public String getMatchedIntraUrlExtension() {
    return matchedIntraUrlExtension;
  }

  public void setMatchedIntraUrlExtension(String matchedIntraUrlExtension) {
    this.matchedIntraUrlExtension = matchedIntraUrlExtension;
  }

  public String getMatchedDividendConnector() {
    return matchedDividendConnector;
  }

  public void setMatchedDividendConnector(String matchedDividendConnector) {
    this.matchedDividendConnector = matchedDividendConnector;
  }

  public String getMatchedDividendUrlExtension() {
    return matchedDividendUrlExtension;
  }

  public void setMatchedDividendUrlExtension(String matchedDividendUrlExtension) {
    this.matchedDividendUrlExtension = matchedDividendUrlExtension;
  }

  public String getMatchedSplitConnector() {
    return matchedSplitConnector;
  }

  public void setMatchedSplitConnector(String matchedSplitConnector) {
    this.matchedSplitConnector = matchedSplitConnector;
  }

  public String getMatchedSplitUrlExtension() {
    return matchedSplitUrlExtension;
  }

  public void setMatchedSplitUrlExtension(String matchedSplitUrlExtension) {
    this.matchedSplitUrlExtension = matchedSplitUrlExtension;
  }

  public Integer getMatchedAssetClassId() {
    return matchedAssetClassId;
  }

  public void setMatchedAssetClassId(Integer matchedAssetClassId) {
    this.matchedAssetClassId = matchedAssetClassId;
  }

  public String getAssetClassMatchType() {
    return assetClassMatchType;
  }

  public void setAssetClassMatchType(String assetClassMatchType) {
    this.assetClassMatchType = assetClassMatchType;
  }

  public Short getRetryHistoryLoad() {
    return retryHistoryLoad;
  }

  public void setRetryHistoryLoad(Short retryHistoryLoad) {
    this.retryHistoryLoad = retryHistoryLoad;
  }

  public Short getRetryIntraLoad() {
    return retryIntraLoad;
  }

  public void setRetryIntraLoad(Short retryIntraLoad) {
    this.retryIntraLoad = retryIntraLoad;
  }

  public Short getRetryDividendLoad() {
    return retryDividendLoad;
  }

  public void setRetryDividendLoad(Short retryDividendLoad) {
    this.retryDividendLoad = retryDividendLoad;
  }

  public Short getRetrySplitLoad() {
    return retrySplitLoad;
  }

  public void setRetrySplitLoad(Short retrySplitLoad) {
    this.retrySplitLoad = retrySplitLoad;
  }

  public Date getSTimestamp() {
    return sTimestamp;
  }

  public void setSTimestamp(Date sTimestamp) {
    this.sTimestamp = sTimestamp;
  }

  public String getHistoryMinDate() {
    return historyMinDate;
  }

  public void setHistoryMinDate(String historyMinDate) {
    this.historyMinDate = historyMinDate;
  }

  public String getHistoryMaxDate() {
    return historyMaxDate;
  }

  public void setHistoryMaxDate(String historyMaxDate) {
    this.historyMaxDate = historyMaxDate;
  }

  public Double getOhlPercentage() {
    return ohlPercentage;
  }

  public void setOhlPercentage(Double ohlPercentage) {
    this.ohlPercentage = ohlPercentage;
  }

  public Integer getDividendCount() {
    return dividendCount;
  }

  public void setDividendCount(Integer dividendCount) {
    this.dividendCount = dividendCount;
  }

  public Integer getSplitCount() {
    return splitCount;
  }

  public void setSplitCount(Integer splitCount) {
    this.splitCount = splitCount;
  }
}
