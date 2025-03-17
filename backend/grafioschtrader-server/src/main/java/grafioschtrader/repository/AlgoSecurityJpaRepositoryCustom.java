package grafioschtrader.repository;

import grafiosch.repository.BaseRepositoryCustom;
import grafioschtrader.algo.AlgoSecurityStrategyImplType;
import grafioschtrader.entities.AlgoSecurity;

public interface AlgoSecurityJpaRepositoryCustom extends BaseRepositoryCustom<AlgoSecurity> {

  AlgoSecurityStrategyImplType getAlgoSecurityStrategyImplTypeByIdSecuritycurrency(Integer idSecuritycurrency);
}
