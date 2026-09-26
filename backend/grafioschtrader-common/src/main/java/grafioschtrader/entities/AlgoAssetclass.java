package grafioschtrader.entities;

import java.util.List;

import grafiosch.common.DynamicFormField;
import grafiosch.common.PropertyAlwaysUpdatable;
import grafioschtrader.algo.strategy.model.StrategyHelper;
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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = AlgoAssetclass.TABNAME)
@DiscriminatorValue(StrategyHelper.ASSET_CLASS_LEVEL_LETTER)
@Schema(description = """
    Asset class level of an algo hierarchy. It always belongs to exactly one AlgoTop and groups the securities of
    that hierarchy either by a referenced asset class or by a custom category name, each with its target percentage.
    Strategies and alerts can be attached at this level.
    """)
public class AlgoAssetclass extends AlgoAssetclassSecurity {

  public static final String TABNAME = "algo_assetclass";

  private static final long serialVersionUID = 1L;

  @Schema(description = "Id of the AlgoTop hierarchy this asset class belongs to. Always set.")
  @Column(name = "id_algo_assetclass_parent")
  @NotNull
  private Integer idAlgoAssetclassParent;

  @Schema(description = """
      Optional custom category name. When set, this node acts as a freeform category (e.g. "Gambling Games")
      under which instruments of any asset class can be added, either from the watchlist of the hierarchy or through
      the instrument search. Mutually exclusive with assetclass: exactly one of the two must be set.""")
  @Column(name = "name")
  @Size(max = 40)
  @PropertyAlwaysUpdatable
  private String name;

  @Schema(description = "Can refer to an asset class. This is the most granular grouping.")
  @JoinColumn(name = "id_asset_class", referencedColumnName = "id_asset_class")
  @ManyToOne
  private Assetclass assetclass;

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

  public List<AlgoSecurity> getAlgoSecurityList() {
    return algoSecurityList;
  }

  public void setAlgoSecurityList(List<AlgoSecurity> algoSecurityList) {
    this.algoSecurityList = algoSecurityList;
  }

}
