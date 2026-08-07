package grafiosch.rest;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;

import grafiosch.integration.entities.Tenant;
import grafiosch.integration.rest.RequestIntegrationMappings;
import grafiosch.test.rest.AbstractUserResourceTest;
import grafiosch.test.rest.UserRegister;

/**
 * Creates the {@code e2e='i'} users of {@code testdata/users.csv} and their tenants. It is the first class of
 * {@link ResourceTestSuite} because every following resource test authenticates as one of these users, and it is what
 * replaced the former {@code IntegrationE2EDataInitializer}: users are no longer inserted with JDBC but registered
 * through the real endpoints, so the registration, mail-verification and role-promotion flow of
 * {@code grafiosch-server-base} is covered rather than bypassed.
 *
 * <p>
 * The four ordered steps live in {@link AbstractUserResourceTest}; only the tenant creation is specific to the
 * application built on the libraries and is supplied here.
 */
@TestMethodOrder(OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
@GrafioschIntegrationTestContext
class UserResourceTest extends AbstractUserResourceTest {

  @Override
  protected void createTenantForUser(UserRegister user) {
    Tenant tenant = new Tenant();
    tenant.setTenantName("Tenant " + user.nickname);
    tenant.setCreateIdUser(user.idUser);

    Tenant created = authenticatedClient(user.nickname)
        .post()
        .uri(RequestIntegrationMappings.TENANT_MAP)
        .body(tenant)
        .exchange()
        .expectStatus().isOk()
        .expectBody(Tenant.class)
        .returnResult()
        .getResponseBody();
    Assertions.assertThat(created.getIdTenant()).isGreaterThan(0);
  }
}
