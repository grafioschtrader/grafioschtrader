package grafioschtrader.entities;

import static javax.persistence.InheritanceType.JOINED;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.Basic;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.DiscriminatorColumn;
import javax.persistence.DiscriminatorType;
import javax.persistence.ElementCollection;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.JoinColumn;
import javax.persistence.MapKeyColumn;
import javax.persistence.Table;

@Entity
@Table(name = "algo_rule_strategy")
@Inheritance(strategy = JOINED)
@DiscriminatorColumn(name = "dtype", discriminatorType = DiscriminatorType.STRING)
public abstract class AlgoRuleStrategy extends TenantBaseID implements Serializable {

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
  @CollectionTable(name = "algo_rule_strategy_param", joinColumns = @JoinColumn(name = "id_algo_rule_strategy"))
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
  public static class AlgoRuleStrategyParam extends AlgoParam {
  }

}
