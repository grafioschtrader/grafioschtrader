package grafioschtrader.repository;

import grafiosch.repository.BaseRepositoryCustom;
import grafioschtrader.entities.Securityaccount;

public interface SecurityaccountJpaRepositoryCustom extends BaseRepositoryCustom<Securityaccount> {

  int delEntityWithTenant(Integer id, Integer idTenant);
}
