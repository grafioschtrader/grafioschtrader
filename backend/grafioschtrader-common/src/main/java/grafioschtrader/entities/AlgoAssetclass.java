package grafioschtrader.entities;

import java.util.List;

import grafioschtrader.algo.strategy.model.AlgoLevelType;
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

  public AlgoAssetclass() {
  }

  public AlgoAssetclass(Integer idTenant, Integer idAlgoAssetclassParent, Assetclass assetclass, Float percentage) {
    this.idTenant = idTenant;
    this.idAlgoAssetclassParent = idAlgoAssetclassParent;
    this.assetclass = assetclass;
    this.percentage = percentage;
  }

  /**
   * List of securities to this asset class which may be used or have a strategy
   */
  @JoinColumn(name = "id_algo_security_parent")
  @OneToMany(cascade = CascadeType.ALL)
  private List<AlgoSecurity> algoSecurityList;

  @Transient
  private Float addedPercentage;

  public Float getAddedPercentage() {
    addedPercentage = (float) algoSecurityList.stream().mapToDouble(algoSecurity -> algoSecurity.getPercentage()).sum();
    return addedPercentage;
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
    return categoryType == null? null: AssetclassType.getAssetClassTypeByValue(categoryType);
  }

  public void setCategoryType(AssetclassType assetClassType) {
    this.categoryType = assetClassType.getValue();
  }

  public SpecialInvestmentInstruments getSpecialInvestmentInstrument() {
    return specialInvestmentInstrument == null? null: SpecialInvestmentInstruments.getSpecialInvestmentInstrumentsByValue(specialInvestmentInstrument);
  }

  public void setSpecialInvestmentInstrument(SpecialInvestmentInstruments specialInvestmentInstrument) {
    this.specialInvestmentInstrument = specialInvestmentInstrument.getValue();
  }


  public List<AlgoSecurity> getAlgoSecurityList() {
    return algoSecurityList;
  }

  public void setAlgoSecurityList(List<AlgoSecurity> algoSecurityList) {
    this.algoSecurityList = algoSecurityList;
  }

}
