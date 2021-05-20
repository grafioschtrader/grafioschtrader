package grafioschtrader.search;

import java.io.Serializable;
import java.util.Date;

import org.springframework.format.annotation.DateTimeFormat;

import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

public class SecuritycurrencySearch implements Serializable {
  private static final long serialVersionUID = 1L;

  public String isin;
  public AssetclassType assetclassType;
  public String name;
  Integer idStockexchange;
  String stockexchangeCounrtyCode;
  String tickerSymbol;
  String currency;

  SpecialInvestmentInstruments specialInvestmentInstruments;
  String subCategoryNLS;
  boolean onlyTenantPrivate;
  Boolean shortSecurity;
  boolean excludeDerivedSecurity;
  
  String idConnectorHistory;
  
  String idConnectorIntra;

  @DateTimeFormat(pattern = "yyyyMMdd")
  public Date activeDate;

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
  
  public String getStockexchangeCounrtyCode() {
    return stockexchangeCounrtyCode;
  }

  public void setStockexchangeCounrtyCode(String stockexchangeCounrtyCode) {
    this.stockexchangeCounrtyCode = stockexchangeCounrtyCode;
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

  public boolean isOnlyTenantPrivate() {
    return onlyTenantPrivate;
  }

  public void setOnlyTenantPrivate(boolean onlyTenantPrivate) {
    this.onlyTenantPrivate = onlyTenantPrivate;
  }

  public Date getActiveDate() {
    return activeDate;
  }

  public void setActiveDate(Date activeDate) {
    this.activeDate = activeDate;
  }

  public Boolean getShortSecurity() {
    return shortSecurity;
  }

  public void setShortSecurity(Boolean shortSecurity) {
    this.shortSecurity = shortSecurity;
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

  @Override
  public String toString() {
    return "SecuritycurrencySearch [isin=" + isin + ", name=" + name + ", tickerSymbol=" + tickerSymbol + ", currency="
        + currency + ", assetclassType=" + assetclassType + ", specialInvestmentInstruments="
        + specialInvestmentInstruments + ", subCategoryNLS=" + subCategoryNLS + ", onlyTenantPrivate="
        + onlyTenantPrivate + ", shortSecurity=" + shortSecurity + ", activeDate=" + activeDate + "]";
  }

}
