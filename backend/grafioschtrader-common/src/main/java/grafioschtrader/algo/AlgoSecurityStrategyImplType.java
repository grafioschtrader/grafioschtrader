package grafioschtrader.algo;

import java.util.Set;

import grafioschtrader.algo.strategy.model.AlgoStrategyImplementationType;
import grafioschtrader.entities.AlgoSecurity;

public class AlgoSecurityStrategyImplType {
  public AlgoSecurity algoSecurity;
  public Set<AlgoStrategyImplementationType> possibleStrategyImplSet;
  public boolean wasCreated;

  public AlgoSecurityStrategyImplType(Set<AlgoStrategyImplementationType> possibleStrategyImplSet) {
    this.possibleStrategyImplSet = possibleStrategyImplSet;
  }

}
