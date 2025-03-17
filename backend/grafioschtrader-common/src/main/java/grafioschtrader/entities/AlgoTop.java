package grafioschtrader.entities;

import grafiosch.common.PropertyAlwaysUpdatable;
import grafioschtrader.algo.RuleStrategyType;
import grafioschtrader.algo.strategy.model.StrategyHelper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Algorithmic trading top level. It does not include the depending children.")
@Entity
@Table(name = AlgoTop.TABNAME)
@DiscriminatorValue(StrategyHelper.TOP_LEVEL_LETTER)
public class AlgoTop extends AlgoTopAssetSecurity {

  public static final String TABNAME = "algo_top";

  private static final long serialVersionUID = 1L;

  @Basic(optional = false)
  @NotNull
  @Size(min = 1, max = 40)
  @PropertyAlwaysUpdatable
  private String name;

//	@JoinColumn(name = "id_algo_assetclass_parent")
//	@OneToMany(fetch = FetchType.LAZY)
//	private List<AlgoAssetclass> algoAssetclassList;

  @Basic(optional = false)
  @Column(name = "rule_or_strategy")
  private Byte ruleStrategy;

  @Schema(description = """
          For the simulation, a watchlist must be linked to the top level. 
          The corresponding securities can then be selected from this list.""")
  @Basic(optional = false)
  @Column(name = "id_watchlist")
  private Integer idWatchlist;

  @Schema(description = """
      A strategy or simulation must be checked for completeness before it is used.""")
  @Column(name = "activatable")
  private boolean activatable;

  @Transient
  public Float addedPercentage;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public RuleStrategyType getRuleStrategy() {
    return RuleStrategyType.getRuleStrategyType(ruleStrategy);
  }

  public void setRuleStrategy(RuleStrategyType ruleStrategy) {
    this.ruleStrategy = ruleStrategy.getValue();
  }

  public Integer getIdWatchlist() {
    return idWatchlist;
  }

  public void setIdWatchlist(Integer idWatchlist) {
    this.idWatchlist = idWatchlist;
  }

  public boolean isActivatable() {
    return activatable;
  }

  public void setActivatable(boolean activatable) {
    this.activatable = activatable;
  }

  @Override
  public String toString() {
    return "AlgoTop [name=" + name + ", ruleStrategy=" + ruleStrategy + ", idWatchlist=" + idWatchlist
        + ", activatable=" + activatable + ", idAlgoAssetclassSecurity=" + idAlgoAssetclassSecurity + ", idTenant="
        + idTenant + ", percentage=" + percentage + "]";
  }

}
