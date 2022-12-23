package grafioschtrader.entities;

import grafioschtrader.algo.RuleStrategy;
import grafioschtrader.common.PropertyAlwaysUpdatable;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Algorithmic tranding top level. It does not include the depending children.
 *
 *
 * @author Hugo Graf
 *
 */
@Entity
@Table(name = AlgoTop.TABNAME)
@DiscriminatorValue("T")
public class AlgoTop extends AlgoTopAssetSecurity {

  public static final String TABNAME = "algo_top";

  private static final long serialVersionUID = 1L;

  @Basic(optional = false)
  @NotNull
  @Size(min = 1, max = 40)
  @PropertyAlwaysUpdatable
  private String name;

//	@JoinColumn(name = "id_algo_assetclass_security_p")
//	@OneToMany(fetch = FetchType.LAZY)
//	private List<AlgoAssetclass> algoAssetclassList;

  @Basic(optional = false)
  @Column(name = "rule_or_strategy")
  private Byte ruleStrategy;

  @Basic(optional = false)
  @Column(name = "id_watchlist")
  private Integer idWatchlist;

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

  public RuleStrategy getRuleStrategy() {
    return RuleStrategy.getRuleStrategy(ruleStrategy);
  }

  public void setRuleStrategy(RuleStrategy ruleStrategy) {
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
