package grafioschtrader.repository;

import grafioschtrader.entities.Cashaccount;

public interface CashaccountJpaRepositoryCustom extends BaseRepositoryCustom<Cashaccount> {

  int delEntityWithTenant(Integer id, Integer idTenant);
}
