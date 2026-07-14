package grafiosch.integration.repository;

import java.lang.annotation.Annotation;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.User;
import grafiosch.integration.entities.Tenant;
import grafiosch.repository.TenantBaseImpl;
import grafiosch.repository.UserJpaRepository;

/** Supplies the reusable tenant operations required by the integration host. */
public class TenantJpaRepositoryImpl extends TenantBaseImpl<Tenant> implements TenantJpaRepositoryCustom {

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

  @Autowired
  private UserJpaRepository userJpaRepository;

  @Override
  @Transactional
  public Tenant saveOnlyAttributes(Tenant tenant, Tenant existingEntity,
      Set<Class<? extends Annotation>> updatePropertyLevelClasses) {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Tenant target = tenant;
    if (tenant.getIdTenant() == null) {
      target.setCreateIdUser(user.getIdUser());
    } else {
      target = tenantJpaRepository.getReferenceById(tenant.getIdTenant());
      target.setTenantName(tenant.getTenantName());
    }
    target = tenantJpaRepository.save(target);
    if (user.getIdTenant() == null) {
      user.setIdTenant(target.getIdTenant());
      userJpaRepository.save(user);
    }
    return target;
  }
}
