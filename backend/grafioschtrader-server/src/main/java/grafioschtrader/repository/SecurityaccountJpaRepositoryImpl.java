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
  SecurityaccountJpaRepository securityaccountJpaRepository;

  @Override
  @Transactional
  @Modifying
  public Securityaccount saveOnlyAttributes(final Securityaccount securityaccount, Securityaccount existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {
    /*
     * Securityaccount createEditSecurityaccount = securityaccount; if
     * (securityaccount.getIdSecuritycashAccount() != null) {
     * createEditSecurityaccount =
     * securityaccountJpaRepository.getOne(securityaccount.getIdSecuritycashAccount(
     * )); createEditSecurityaccount.updateThis(securityaccount); } return
     * securityaccountJpaRepository.save(createEditSecurityaccount);
     */

    return RepositoryHelper.saveOnlyAttributes(securityaccountJpaRepository, securityaccount, existingEntity,
        updatePropertyLevelClasses);
  }

}
