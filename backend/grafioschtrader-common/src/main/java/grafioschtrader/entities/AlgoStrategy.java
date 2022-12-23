package grafioschtrader.entities;

import grafioschtrader.algo.strategy.model.AlgoStrategyImplementations;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A tenant portfolios, asset class or security may have none or more strategy,
 * this class contains a single strategy.
 *
 * @author Hugo Graf
 *
 */
@Entity
@Table(name = AlgoStrategy.TABNAME)
@DiscriminatorValue("S")
public class AlgoStrategy extends AlgoRuleStrategy {

  public static final String TABNAME = "algo_strategy";

  private static final long serialVersionUID = 1L;

  @Basic(optional = false)
  @Column(name = "algo_strategy_impl")
  private byte algoStrategyImplementations;

  public AlgoStrategyImplementations getAlgoStrategyImplementations() {
    return AlgoStrategyImplementations.getAlgoStrategyImplentaions(this.algoStrategyImplementations);
  }

  public void setAlgoStrategyImplementations(AlgoStrategyImplementations algoStrategyImplementations) {
    this.algoStrategyImplementations = algoStrategyImplementations.getValue();
  }

}
