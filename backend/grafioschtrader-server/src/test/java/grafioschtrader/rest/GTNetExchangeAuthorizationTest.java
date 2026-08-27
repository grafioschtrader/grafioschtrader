package grafioschtrader.rest;

import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;

import grafiosch.error.SecurityBreachError;
import grafioschtrader.dto.GTSecuritiyCurrencyExchange;
import grafioschtrader.entities.Security;

/**
 * Guards who may change the GTNet exchange flags of a single instrument.
 *
 * <p>
 * The four flags decide which instruments this instance offers to its GTNet peers and which it wants to receive. They
 * are columns of a shared instrument rather than tenant data, and they are not reached by the administrator-only
 * request matchers that cover the other GTNet paths, because the endpoint lives under {@code /api/security}. The rule
 * is therefore the ordinary editing right of the instrument: an administrator and a user with the extended editing
 * right may change every instrument, everybody else only the instruments they created themselves.
 * </p>
 *
 * <p>
 * The test writes the flags a row already carries, so it changes no state and needs no cleanup, and it creates no
 * instrument of its own: it runs after {@link SecurityResourceTest}, which assigns its securities to random fixture
 * users, and picks the rows it needs from that population.
 * </p>
 */
@TestMethodOrder(OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
class GTNetExchangeAuthorizationTest extends BaseIntegrationTest {

  private static final String EXCHANGE_PATH = RequestGTMappings.SECURITY_MAP + "/gtnetexchange";
  private static final String BATCH_PATH = EXCHANGE_PATH + "/batch";

  @BeforeAll
  void setUpUserToken() {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
  }

  @Test
  @Order(5)
  @DisplayName("Reading the exchange configuration is open to a user without a privileged role")
  void readIsOpenToEveryUser() {
    Assertions.assertThat(readSecurities(RestTestHelper.LIMIT1)).isNotEmpty();
  }

  @Test
  @Order(10)
  @DisplayName("The administrator writes the exchange flags of a security created by somebody else")
  void administratorWritesAnySecurity() {
    Security foreign = firstSecurity(false, RestTestHelper.LIMIT1);
    expectAccepted(RestTestHelper.ADMIN, foreign);
  }

  @Test
  @Order(15)
  @DisplayName("The owner without a privileged role writes the exchange flags of their own security")
  void ownerWritesOwnSecurity() {
    Security own = firstSecurity(true, RestTestHelper.LIMIT1);
    expectAccepted(RestTestHelper.LIMIT1, own);
  }

  @Test
  @Order(20)
  @DisplayName("A user without a privileged role may not write the exchange flags of a foreign security")
  void strangerIsRefused() {
    Security foreign = firstSecurity(false, RestTestHelper.LIMIT1);
    authenticatedClient(RestTestHelper.LIMIT1)
        .post()
        .uri(BATCH_PATH)
        .body(List.of(foreign))
        .exchange()
        .expectStatus().isUnauthorized()
        .expectBody(String.class)
        .value(body -> Assertions.assertThat(body).contains(SecurityBreachError.class.getSimpleName()));
  }

  /**
   * Posts the flags the security already carries and asserts that they come back unchanged. Writing the current values
   * proves the authorization decision without changing any state.
   *
   * @param nickname the user to authenticate as
   * @param security the security to write
   */
  private void expectAccepted(String nickname, Security security) {
    Security[] updated = authenticatedClient(nickname)
        .post()
        .uri(BATCH_PATH)
        .body(List.of(security))
        .exchange()
        .expectStatus().isOk()
        .expectBody(Security[].class)
        .returnResult()
        .getResponseBody();

    Assertions.assertThat(updated).hasSize(1);
    Assertions.assertThat(updated[0].getIdSecuritycurrency()).isEqualTo(security.getIdSecuritycurrency());
    Assertions.assertThat(updated[0].isGtNetLastpriceRecv()).isEqualTo(security.isGtNetLastpriceRecv());
    Assertions.assertThat(updated[0].isGtNetHistoricalRecv()).isEqualTo(security.isGtNetHistoricalRecv());
    Assertions.assertThat(updated[0].isGtNetLastpriceSend()).isEqualTo(security.isGtNetLastpriceSend());
    Assertions.assertThat(updated[0].isGtNetHistoricalSend()).isEqualTo(security.isGtNetHistoricalSend());
  }

  /**
   * Picks the first security that is, or is not, owned by the given fixture user.
   *
   * @param owned    whether the security must belong to the user or must not
   * @param nickname the fixture user whose ownership decides
   * @return the matching security
   */
  private Security firstSecurity(boolean owned, String nickname) {
    Integer idUser = RestTestHelper.getUserByNickname(nickname).idUser;
    List<Security> securities = readSecurities(RestTestHelper.ADMIN);
    return securities.stream().filter(s -> idUser.equals(s.getCreatedBy()) == owned).findFirst()
        .orElseGet(() -> {
          Assumptions.abort("No security " + (owned ? "created by " : "created without ") + nickname
              + " exists; SecurityResourceTest assigns its owners at random");
          return null;
        });
  }

  /**
   * Reads the securities with their GTNet exchange configuration, inactive ones included.
   *
   * @param nickname the user to authenticate as
   * @return the securities as the endpoint returns them
   */
  private List<Security> readSecurities(String nickname) {
    SecurityExchange body = authenticatedClient(nickname)
        .get()
        .uri(uriBuilder -> uriBuilder.path(EXCHANGE_PATH).queryParam("activeOnly", false).build())
        .exchange()
        .expectStatus().isOk()
        .expectBody(SecurityExchange.class)
        .returnResult()
        .getResponseBody();

    Assertions.assertThat(body).isNotNull();
    return body.securitiescurrenciesList;
  }

  /** Binds the generic response of the endpoint so that it can be deserialized without a type reference. */
  static class SecurityExchange extends GTSecuritiyCurrencyExchange<Security> {
  }
}
