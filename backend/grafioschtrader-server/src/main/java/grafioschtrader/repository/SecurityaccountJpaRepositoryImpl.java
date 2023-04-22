package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.Securityaccount;

public class SecurityaccountJpaRepositoryImpl extends BaseRepositoryImpl<Securityaccount>
    implements SecurityaccountJpaRepositoryCustom {

  @Autowired
  private SecurityaccountJpaRepository securityaccountJpaRepository;

  @Override
  @Transactional
  @Modifying
  public Securityaccount saveOnlyAttributes(final Securityaccount securityaccount, Securityaccount existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {
    return RepositoryHelper.saveOnlyAttributes(securityaccountJpaRepository, securityaccount, existingEntity,
        updatePropertyLevelClasses);
  }

 

  @Override
  public int delEntityWithTenant(Integer id, Integer idTenant) {
    return securityaccountJpaRepository.deleteSecurityaccount(id, idTenant);
  }

}
