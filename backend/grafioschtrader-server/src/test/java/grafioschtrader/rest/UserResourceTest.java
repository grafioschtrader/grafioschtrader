package grafioschtrader.rest;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;

import grafiosch.test.rest.AbstractUserResourceTest;
import grafiosch.test.rest.UserRegister;
import grafioschtrader.entities.Tenant;
import grafioschtrader.types.TenantKindType;

/**
 * Creates the {@code e2e='i'} users of {@code testdata/users.json} and their tenants. It is the first class of
 * {@link ResoureTestSuite} because every following resource test authenticates as one of these users.
 *
 * <p>
 * The four ordered steps — register, verify the mail token, promote the role, create the tenant — are implemented in
 * {@link AbstractUserResourceTest}; only the last one is Grafioschtrader specific and is supplied here.
 */
@TestMethodOrder(OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
@GTIntegrationTestContext
class UserResourceTest extends AbstractUserResourceTest {

  @Override
  protected void createTenantForUser(UserRegister user) {
    Tenant tenant = new Tenant("Tenant " + user.nickname, user.currency, user.idUser, TenantKindType.MAIN, false);
    Tenant created = authenticatedClient(user.nickname)
        .post()
        .uri(RequestGTMappings.TENANT_MAP)
        .body(tenant)
        .exchange()
        .expectStatus().isOk()
        .expectBody(Tenant.class)
        .returnResult()
        .getResponseBody();
    Assertions.assertThat(created.getIdTenant()).isGreaterThan(0);
  }

  /**
   * Grafioschtrader is served below the {@code /grafioschtrader} context path, so the verification link in the
   * registration mail has to be built from that base URL rather than from the bare host.
   */
  @Override
  protected String getRegistrationReferer() {
    return "http://localhost:" + port + "/grafioschtrader/register";
  }
}
