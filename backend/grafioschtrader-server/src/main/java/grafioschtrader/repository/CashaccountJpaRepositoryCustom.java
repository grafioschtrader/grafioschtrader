package grafioschtrader.repository;

import grafiosch.repository.BaseRepositoryCustom;
import grafioschtrader.entities.Cashaccount;

public interface CashaccountJpaRepositoryCustom extends BaseRepositoryCustom<Cashaccount> {

  int delEntityWithTenant(Integer id, Integer idTenant);
}
