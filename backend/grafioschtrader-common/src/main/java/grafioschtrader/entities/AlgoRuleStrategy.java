package grafioschtrader.entities;

import static jakarta.persistence.InheritanceType.JOINED;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import jakarta.persistence.Basic;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = AlgoRuleStrategy.TABNAME)
@Inheritance(strategy = JOINED)
@DiscriminatorColumn(name = "dtype", discriminatorType = DiscriminatorType.STRING)
public abstract class AlgoRuleStrategy extends TenantBaseID implements Serializable {

  public static final String TABNAME = "algo_rule_strategy";
  public static final String ALGO_RULE_STRATEGY_PARAM = "algo_rule_strategy_param";

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_algo_rule_strategy")
  protected Integer idAlgoRuleStrategy;

  @Basic(optional = false)
  @Column(name = "id_algo_assetclass_security")
  protected Integer idAlgoAssetclassSecurity;

  @Column(name = "id_tenant")
  protected Integer idTenant;

  @ElementCollection(fetch = FetchType.EAGER)
  @MapKeyColumn(name = "param_name")
  @CollectionTable(name = ALGO_RULE_STRATEGY_PARAM, joinColumns = @JoinColumn(name = "id_algo_rule_strategy"))
  private Map<String, AlgoRuleStrategyParam> algoRuleStrategyParamMap = new HashMap<>();

  public Integer getIdAlgoRuleStrategy() {
    return idAlgoRuleStrategy;
  }

  public void setIdAlgoRuleStrategy(Integer idAlgoRuleStrategy) {
    this.idAlgoRuleStrategy = idAlgoRuleStrategy;
  }

  public Integer getIdAlgoAssetclassSecurity() {
    return idAlgoAssetclassSecurity;
  }

  public void setIdAlgoAssetclassSecurity(Integer idAlgoAssetclassSecurity) {
    this.idAlgoAssetclassSecurity = idAlgoAssetclassSecurity;
  }

  @Override
  public Integer getIdTenant() {
    return idTenant;
  }

  @Override
  public void setIdTenant(Integer idTenant) {
    this.idTenant = idTenant;
  }

  public Map<String, AlgoRuleStrategyParam> getAlgoRuleStrategyParamMap() {
    return algoRuleStrategyParamMap;
  }

  public void setAlgoRuleStrategyParamMap(Map<String, AlgoRuleStrategyParam> algoRuleStrategyParamMap) {
    this.algoRuleStrategyParamMap = algoRuleStrategyParamMap;
  }

  @Override
  public Integer getId() {
    return idAlgoRuleStrategy;
  }

  @Embeddable
  public static class AlgoRuleStrategyParam extends BaseParam {
  }

}
