package grafioschtrader.entities;

import static jakarta.persistence.InheritanceType.JOINED;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

import grafioschtrader.algo.rule.BuySell;
import jakarta.persistence.Basic;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * Entity which is the base class for all levels assignment (tenant portfolio,
 * asset class, security)
 *
 * @author Hugo Graf
 *
 */
@Entity
@Table(name = AlgoTopAssetSecurity.TABNAME)
@Inheritance(strategy = JOINED)
@DiscriminatorColumn(name = "dtype", discriminatorType = DiscriminatorType.STRING)
public abstract class AlgoTopAssetSecurity extends TenantBaseID implements Serializable {

  public static final String TABNAME = "algo_top_asset_security";

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_algo_assetclass_security")
  protected Integer idAlgoAssetclassSecurity;

  @JoinColumn(name = "id_algo_assetclass_security")
  @OneToMany(cascade = CascadeType.REMOVE, fetch = FetchType.EAGER)
  private List<AlgoRuleStrategy> algoRuleStrategyList;

  @Column(name = "id_tenant")
  protected Integer idTenant;

  @Column(name = "percentage")
  protected Float percentage;

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

  public List<AlgoStrategy> getAlgoStrategyList() {
    return (algoRuleStrategyList != null)
        ? this.algoRuleStrategyList.stream().filter(algoRuleStrategy -> algoRuleStrategy instanceof AlgoStrategy)
            .map(algoStrategy -> (AlgoStrategy) algoStrategy).collect(Collectors.toList())
        : null;
  }

  public List<AlgoRule> getAlgoSellRuleList() {
    return getAlgoRuleList(BuySell.BS_SELL);
  }

  public List<AlgoRule> getAlgoBuyRuleList() {
    return getAlgoRuleList(BuySell.BS_BUY);
  }

  private List<AlgoRule> getAlgoRuleList(BuySell buyOrSell) {
    return (algoRuleStrategyList != null) ? this.algoRuleStrategyList.stream()
        .filter(algoRuleStrategy -> algoRuleStrategy instanceof AlgoRule).map(algoRule -> (AlgoRule) algoRule)
        .filter(algoRule -> algoRule.getBuySell() == buyOrSell).collect(Collectors.toList()) : null;
  }

  public Float getPercentage() {
    return percentage;
  }

  public void setPercentage(Float percentage) {
    this.percentage = percentage;
  }

  @Override
  public Integer getId() {
    return idAlgoAssetclassSecurity;
  }

}
