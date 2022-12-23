/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package grafioschtrader.entities;

import java.io.Serializable;
import java.util.EnumMap;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.util.Assert;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.common.PropertyAlwaysUpdatable;
import grafioschtrader.common.PropertySelectiveUpdatableOrWhenNull;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.Language;
import grafioschtrader.types.SpecialInvestmentInstruments;
import grafioschtrader.validation.ValidMultilanguage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Cacheable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;


@Entity
@Table(name = Assetclass.TABNAME)
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Schema(description = """
Each security must have an asset class. The selection of an asset class has a significant impact on the characteristics
of an instrument when it is recorded. It also influences the reporting and the type of transaction that can be applied to an instrument.
""")
public class Assetclass extends Auditable implements Serializable {

  public static final String TABNAME = "assetclass";

  public static EnumMap<AssetclassType, SpecialInvestmentInstruments[]> possibleInstrumentsMap = new EnumMap<>(
      AssetclassType.class);

  static {
    possibleInstrumentsMap.put(AssetclassType.EQUITIES,
        new SpecialInvestmentInstruments[] { SpecialInvestmentInstruments.DIRECT_INVESTMENT,
            SpecialInvestmentInstruments.ETF, SpecialInvestmentInstruments.MUTUAL_FUND,
            SpecialInvestmentInstruments.PENSION_FUNDS, SpecialInvestmentInstruments.CFD,
            SpecialInvestmentInstruments.ISSUER_RISK_PRODUCT, SpecialInvestmentInstruments.NON_INVESTABLE_INDICES });

    possibleInstrumentsMap.put(AssetclassType.FIXED_INCOME,
        new SpecialInvestmentInstruments[] { SpecialInvestmentInstruments.DIRECT_INVESTMENT,
            SpecialInvestmentInstruments.ETF, SpecialInvestmentInstruments.MUTUAL_FUND,
            SpecialInvestmentInstruments.PENSION_FUNDS, SpecialInvestmentInstruments.ISSUER_RISK_PRODUCT,
            SpecialInvestmentInstruments.NON_INVESTABLE_INDICES });

    possibleInstrumentsMap.put(AssetclassType.MONEY_MARKET,
        new SpecialInvestmentInstruments[] { SpecialInvestmentInstruments.DIRECT_INVESTMENT,
            SpecialInvestmentInstruments.ETF, SpecialInvestmentInstruments.MUTUAL_FUND });

    possibleInstrumentsMap.put(AssetclassType.COMMODITIES,
        new SpecialInvestmentInstruments[] { SpecialInvestmentInstruments.DIRECT_INVESTMENT,
            SpecialInvestmentInstruments.ETF, SpecialInvestmentInstruments.MUTUAL_FUND,
            SpecialInvestmentInstruments.CFD, SpecialInvestmentInstruments.ISSUER_RISK_PRODUCT,
            SpecialInvestmentInstruments.NON_INVESTABLE_INDICES });

    possibleInstrumentsMap.put(AssetclassType.REAL_ESTATE,
        new SpecialInvestmentInstruments[] { SpecialInvestmentInstruments.ETF, SpecialInvestmentInstruments.MUTUAL_FUND,
            SpecialInvestmentInstruments.ISSUER_RISK_PRODUCT, SpecialInvestmentInstruments.NON_INVESTABLE_INDICES });

    possibleInstrumentsMap.put(AssetclassType.MULTI_ASSET,
        new SpecialInvestmentInstruments[] { SpecialInvestmentInstruments.ETF, SpecialInvestmentInstruments.MUTUAL_FUND,
            SpecialInvestmentInstruments.PENSION_FUNDS, SpecialInvestmentInstruments.ISSUER_RISK_PRODUCT,
            SpecialInvestmentInstruments.NON_INVESTABLE_INDICES });

    possibleInstrumentsMap.put(AssetclassType.CONVERTIBLE_BOND,
        new SpecialInvestmentInstruments[] { SpecialInvestmentInstruments.DIRECT_INVESTMENT,
            SpecialInvestmentInstruments.ETF, SpecialInvestmentInstruments.MUTUAL_FUND,
            SpecialInvestmentInstruments.PENSION_FUNDS, SpecialInvestmentInstruments.NON_INVESTABLE_INDICES });

    possibleInstrumentsMap.put(AssetclassType.CREDIT_DERIVATIVE,
        new SpecialInvestmentInstruments[] { SpecialInvestmentInstruments.ETF, SpecialInvestmentInstruments.MUTUAL_FUND,
            SpecialInvestmentInstruments.ISSUER_RISK_PRODUCT, SpecialInvestmentInstruments.NON_INVESTABLE_INDICES });

    possibleInstrumentsMap.put(AssetclassType.CURRENCY_PAIR, new SpecialInvestmentInstruments[] {
        SpecialInvestmentInstruments.ISSUER_RISK_PRODUCT, SpecialInvestmentInstruments.FOREX, 
        SpecialInvestmentInstruments.ETF });

  }

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_asset_class")
  private Integer idAssetClass;

  @Basic(optional = false)
  @NotNull
  @Column(name = "category_type")
  @PropertySelectiveUpdatableOrWhenNull
  private byte categoryType;

  @NotNull
  @Column(name = "spec_invest_instrument")
  @PropertySelectiveUpdatableOrWhenNull
  private byte specialInvestmentInstrument;

  @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
  @OneToOne(fetch = FetchType.EAGER, orphanRemoval = true, cascade = CascadeType.ALL)
  @JoinColumn(name = "sub_category_nls")
  @ValidMultilanguage
  @PropertyAlwaysUpdatable
  private MultilanguageString subCategoryNLS;

  public Assetclass() {
  }

  public Assetclass(AssetclassType assetClassType, String subCategory,
      SpecialInvestmentInstruments specialInvestmentInstrument, Language language) {
    this.categoryType = assetClassType.getValue();
    this.setSubCategoryByLanguage(subCategory, language);
    this.specialInvestmentInstrument = specialInvestmentInstrument.getValue();
  }

  public Assetclass(AssetclassType assetClassType, SpecialInvestmentInstruments specialInvestmentInstrument,
      String subCategoryDE, String subCategoryEN) {
    this.categoryType = assetClassType.getValue();
    this.specialInvestmentInstrument = specialInvestmentInstrument.getValue();
    this.setSubCategoryByLanguage(subCategoryDE, Language.GERMAN);
    this.setSubCategoryByLanguage(subCategoryEN, Language.ENGLISH);
  }

  @JsonIgnore
  @Override
  public Integer getId() {
    return idAssetClass;
  }

  public AssetclassType getCategoryType() {
    return AssetclassType.getAssetClassTypeByValue(this.categoryType);
  }

  public void setCategoryType(AssetclassType assetClassType) {
    Assert.notNull(assetClassType, "Asset clas mustn't be null!");
    this.categoryType = assetClassType.getValue();
  }

  public SpecialInvestmentInstruments getSpecialInvestmentInstrument() {
    return SpecialInvestmentInstruments.getSpecialInvestmentInstrumentsByValue(specialInvestmentInstrument);
  }

  public void setSpecialInvestmentInstrument(SpecialInvestmentInstruments specialInvestmentInstrument) {
    Assert.notNull(specialInvestmentInstrument, "Investment Instrument mustn't be null!");
    this.specialInvestmentInstrument = specialInvestmentInstrument.getValue();
  }

  public void setSubCategoryByLanguage(final String subCategory, final Language language) {
    getSubCategoryNLS().addText(language.getKey(), subCategory);
  }

  public String getSubCategoryByLanguage(final Language language) {
    return getSubCategoryNLS().getText(language.getKey());
  }

  public MultilanguageString getSubCategoryNLS() {
    return subCategoryNLS != null ? subCategoryNLS : (subCategoryNLS = new MultilanguageString());
  }

  public Integer getIdAssetClass() {
    return idAssetClass;
  }

  public void setIdAssetClass(Integer idAssetClass) {
    this.idAssetClass = idAssetClass;
  }

  public void setSubCategoryNLS(MultilanguageString subCategoryNLS) {
    this.subCategoryNLS = subCategoryNLS;
  }

  @Override
  public String toString() {
    return "grafioschtrader.entities.Assetclass[ idAssetClass=" + idAssetClass + " ]";
  }

}
