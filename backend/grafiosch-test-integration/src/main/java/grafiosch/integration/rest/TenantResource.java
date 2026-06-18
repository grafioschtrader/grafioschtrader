package grafiosch.integration.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.entities.User;
import grafiosch.integration.entities.Tenant;
import grafiosch.integration.repository.TenantJpaRepository;
import grafiosch.repository.TenantBaseCustom;
import grafiosch.rest.TenantBaseResource;
import grafiosch.rest.UpdateCreateJpaRepository;


@RestController
@RequestMapping(RequestIntegrationMappings.TENANT_MAP)
public class TenantResource extends TenantBaseResource<Tenant> {

  @Autowired
  private TenantJpaRepository tenantJpaRepository;


  @Override
  protected UpdateCreateJpaRepository<Tenant> getUpdateCreateJpaRepository() {
    return tenantJpaRepository;
  }


  @Override
  protected TenantBaseCustom getTenantRepository() {
    return tenantJpaRepository;
  }

  @Override
  protected Integer createManagedClientTenant(String tenantName, User advisor) {
    Tenant tenant = new Tenant();
    tenant.setTenantName(tenantName);
    tenant.setCreateIdUser(advisor.getIdUser());
    return tenantJpaRepository.save(tenant).getIdTenant();
  }

  @Override
  protected String getManagedTenantName(Integer idTenant) {
    return tenantJpaRepository.findById(idTenant).map(Tenant::getTenantName).orElse(null);
  }
}
