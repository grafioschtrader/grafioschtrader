package grafioschtrader.gtnet.model;

import java.util.Date;
import java.util.List;

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

  // Stock exchange (MIC code for cross-instance mapping)
  @Schema(description = "ISO 10383 Market Identifier Code of the stock exchange")
  private String stockexchangeMic;

  @Schema(description = "Stock exchange name as fallback if MIC is not available")
  private String stockexchangeName;

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

  // Source tracking
  @Schema(description = "Domain name of the GTNet peer that provided this data")
  private String sourceDomain;

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
}
