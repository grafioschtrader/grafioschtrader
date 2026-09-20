package grafioschtrader.entities;

import java.util.List;

import grafiosch.common.DynamicFormField;
import grafiosch.common.PropertyAlwaysUpdatable;
import grafioschtrader.algo.strategy.model.StrategyHelper;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = AlgoAssetclass.TABNAME)
@DiscriminatorValue(StrategyHelper.ASSET_CLASS_LEVEL_LETTER)
@Schema(description = """
    Strategies or alerts can be defined at the level of an asset class.
    This can be done independently of the existence of a top level definition.
    The strategy would then be applied directly to all portfolios. If a portfolio is specified,
    the strategy is only applied to this portfolio.
    """)
public class AlgoAssetclass extends AlgoAssetclassSecurity {

  public static final String TABNAME = "algo_assetclass";

  private static final long serialVersionUID = 1L;

  @Schema(description = """
      Refers to membership of the top level. Can also be zero and then refer to a securities account.
      If both are zero, it applies to all securities accounts.""")
  @Column(name = "id_algo_assetclass_parent")
  private Integer idAlgoAssetclassParent;

  @Schema(description = """
      Whether this bucket takes part in evaluation. Deactivating it suppresses every alert scoped through it without
      losing their configuration, and it re-establishes their crossing baselines when the bucket is switched on
      again.""")
  @Column(name = "activatable")
  private boolean activatable = true;

  @Schema(description = """
      Optional custom category name. When set, this node acts as a freeform category (e.g. "Gambling Games")
      under which any watchlist security can be added, regardless of asset class. Mutually exclusive with
      assetclass, categoryType, and specialInvestmentInstrument — if name is non-null, those fields must be null.""")
  @Column(name = "name")
  @Size(max = 40)
  @PropertyAlwaysUpdatable
  private String name;

  @Schema(description = "Can refer to an asset class. This is the most granular grouping.")
  @JoinColumn(name = "id_asset_class", referencedColumnName = "id_asset_class")
  @ManyToOne
  private Assetclass assetclass;

  @Schema(description = """
      An instrument belongs to a group of asset classes, such as shares, bonds, etc.
      This can be used alone or with the type of instrument as an AND operation. If defined, the asset class must be null.""")
  @Column(name = "category_type")
  private Byte categoryType;

  @Schema(description = """
      An instrument belongs to a group of financial instruments, e.g. ETF, direct, etc.
      This can be defined alone or with the type of asset class as an AND operation. If defined, the asset class must be zero.""")
  @Column(name = "spec_invest_instrument")
  private Byte specialInvestmentInstrument;

  @Column(name = "security_deviation_percentage")
  @Min(0)
  @Max(100)
  @DynamicFormField(uiOrder = "1.1", fractionLimit = 2)
  @Schema(description = "Security allocation band in percentage points of the target class amount; null inherits the portfolio setting")
  private Double securityDeviationPercentage;

  @Column(name = "max_traded_securities_per_assetclass")
  @Min(1)
  @DynamicFormField(uiOrder = "1.2")
  @Schema(description = "Maximum distinct securities per class checkpoint; null inherits the portfolio setting")
  private Integer maxTradedSecuritiesPerAssetclass;

  public Double getSecurityDeviationPercentage() {
    return securityDeviationPercentage;
  }

  public void setSecurityDeviationPercentage(Double value) {
    securityDeviationPercentage = value;
  }

  public Integer getMaxTradedSecuritiesPerAssetclass() {
    return maxTradedSecuritiesPerAssetclass;
  }

  public void setMaxTradedSecuritiesPerAssetclass(Integer value) {
    maxTradedSecuritiesPerAssetclass = value;
  }

  public AlgoAssetclass() {
  }

  public AlgoAssetclass(Integer idTenant, Integer idAlgoAssetclassParent, Assetclass assetclass, Float percentage) {
    this.idTenant = idTenant;
    this.idAlgoAssetclassParent = idAlgoAssetclassParent;
    this.assetclass = assetclass;
    this.percentage = percentage;
  }

  @Schema(description = "Securities assigned to this asset class that may be used by a strategy")
  @JoinColumn(name = "id_algo_security_parent")
  @OneToMany(cascade = CascadeType.ALL)
  private List<AlgoSecurity> algoSecurityList;

  @Transient
  @Schema(description = "Sum of the target percentages of the securities assigned to this asset class", accessMode = Schema.AccessMode.READ_ONLY)
  private Float addedPercentage;

  public Float getAddedPercentage() {
    addedPercentage = algoSecurityList == null ? 0f
        : (float) algoSecurityList.stream()
            .mapToDouble(algoSecurity -> algoSecurity.getPercentage() == null ? 0 : algoSecurity.getPercentage()).sum();
    return addedPercentage;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public boolean isActivatable() {
    return activatable;
  }

  public void setActivatable(boolean activatable) {
    this.activatable = activatable;
  }

  public Integer getIdAlgoAssetclassParent() {
    return idAlgoAssetclassParent;
  }

  public void setIdAlgoAssetclassParent(Integer idAlgoAssetclassParent) {
    this.idAlgoAssetclassParent = idAlgoAssetclassParent;
  }

  public Assetclass getAssetclass() {
    return assetclass;
  }

  public void setAssetclass(Assetclass assetclass) {
    this.assetclass = assetclass;
  }

  public AssetclassType getCategoryType() {
    return categoryType == null ? null : AssetclassType.getAssetClassTypeByValue(categoryType);
  }

  public void setCategoryType(AssetclassType assetClassType) {
    this.categoryType = assetClassType == null ? null : assetClassType.getValue();
  }

  public SpecialInvestmentInstruments getSpecialInvestmentInstrument() {
    return specialInvestmentInstrument == null ? null
        : SpecialInvestmentInstruments.getSpecialInvestmentInstrumentsByValue(specialInvestmentInstrument);
  }

  public void setSpecialInvestmentInstrument(SpecialInvestmentInstruments specialInvestmentInstrument) {
    this.specialInvestmentInstrument = specialInvestmentInstrument == null ? null
        : specialInvestmentInstrument.getValue();
  }

  public List<AlgoSecurity> getAlgoSecurityList() {
    return algoSecurityList;
  }

  public void setAlgoSecurityList(List<AlgoSecurity> algoSecurityList) {
    this.algoSecurityList = algoSecurityList;
  }

}
