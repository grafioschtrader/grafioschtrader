package grafioschtrader.repository;

import grafioschtrader.algo.AlgoSecurityStrategyImplType;
import grafioschtrader.entities.AlgoSecurity;

public interface AlgoSecurityJpaRepositoryCustom extends BaseRepositoryCustom<AlgoSecurity> {

  AlgoSecurityStrategyImplType getAlgoSecurityStrategyImplTypeByIdSecuritycurrency(Integer idSecuritycurrency);
}
