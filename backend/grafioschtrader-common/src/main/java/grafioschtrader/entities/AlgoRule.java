package grafioschtrader.entities;

import java.util.HashMap;
import java.util.Map;

import grafioschtrader.algo.rule.BuySell;
import jakarta.persistence.Basic;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Table;

@Entity
@Table(name = AlgoRule.TABNAME)
@DiscriminatorValue("R")
public class AlgoRule extends AlgoRuleStrategy {
  public static final String TABNAME = "algo_rule";
  public static final String ALGO_RULE_PARAM2 = "algo_rule_param2";

  private static final long serialVersionUID = 1L;

  @Basic(optional = false)
  @Column(name = "buy_sell")
  private byte buySell;

  @Basic(optional = false)
  @Column(name = "and_or_not")
  private byte andOrNot;

  @Basic(optional = false)
  @Column(name = "trading_rule")
  private byte tradingRule;

  @Basic(optional = false)
  @Column(name = "rule_param1")
  private byte ruleParam1;

  @Column(name = "rule_param2")
  private byte ruleParam2;

  @ElementCollection(fetch = FetchType.EAGER)
  @MapKeyColumn(name = "param_name")
  @CollectionTable(name = ALGO_RULE_PARAM2, joinColumns = @JoinColumn(name = "id_algo_rule_strategy"))
  private Map<String, AlgoRuleParam2> AlgoRuleParam2Map = new HashMap<>();

  public BuySell getBuySell() {
    return BuySell.getBuySell(buySell);
  }

  public void setBuySell(BuySell buySell) {
    this.buySell = buySell.getValue();
  }

  public byte getAndOrNot() {
    return andOrNot;
  }

  public void setAndOrNot(byte andOrNot) {
    this.andOrNot = andOrNot;
  }

  public byte getTradingRule() {
    return tradingRule;
  }

  public void setTradingRule(byte tradingRule) {
    this.tradingRule = tradingRule;
  }

  public byte getRuleParam1() {
    return ruleParam1;
  }

  public void setRuleParam1(byte ruleParam1) {
    this.ruleParam1 = ruleParam1;
  }

  public byte getRuleParam2() {
    return ruleParam2;
  }

  public void setRuleParam2(byte ruleParam2) {
    this.ruleParam2 = ruleParam2;
  }

  public Map<String, AlgoRuleParam2> getAlgoRuleParam2Map() {
    return AlgoRuleParam2Map;
  }

  public void setAlgoRuleParam2Map(Map<String, AlgoRuleParam2> algoRuleParam2Map) {
    AlgoRuleParam2Map = algoRuleParam2Map;
  }

  @Embeddable
  @MappedSuperclass
  public static class AlgoRuleParam2 extends BaseParam {
  }

}
