package grafioschtrader.repository;

import grafioschtrader.entities.Securityaccount;

public interface SecurityaccountJpaRepositoryCustom extends BaseRepositoryCustom<Securityaccount> {

  int delEntityWithTenant(Integer id, Integer idTenant);
}
