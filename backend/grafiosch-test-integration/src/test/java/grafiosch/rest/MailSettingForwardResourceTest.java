package grafiosch.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.core.ParameterizedTypeReference;

import grafiosch.entities.MailSettingForward;
import grafiosch.types.MessageComType;
import grafiosch.types.MessageTargetType;

/**
 * Full life cycle of a user's mail forwarding rule: read the defaults, create a rule, change its target, list it and
 * delete it again.
 *
 * <p>
 * The class deletes any leftover rule of the acting user before it creates one, so it can be rerun against the same
 * {@code grafiosch_t} without a database reset — the same rule the browser specs follow.
 *
 * <p>
 * The message type must be one of the keys of {@code MailSendForwardDefaultBase.mailSendForwardDefaultMap}; the
 * repository reads {@code canRedirect} from that map without a null check, so an unconfigured type such as
 * {@code USER_GENERAL_PURPOSE_USER_TO_USER} produces a {@code NullPointerException} instead of a validation error.
 */
@TestMethodOrder(OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
class MailSettingForwardResourceTest extends BaseIntegrationTest {

  private Integer idMailSettingForward;

  @BeforeAll
  void setUpUserToken() {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
  }

  @Order(1)
  @Test
  @DisplayName("The defaults and the selectable values are served")
  void getSendForwardDefault() {
    // Asserted on the raw JSON rather than on MailSendForwardDefaultBase: its maps are keyed by the interface
    // IMessageComType, for which Jackson has no map key deserializer. The frontend reads this response as plain JSON,
    // so the payload - not a Java round trip - is the contract worth pinning here.
    String defaults = authenticatedClient(RestTestHelper.USER)
        .get()
        .uri(RequestMappings.MAIL_SETTING_FORWARD_MAP + "/defaultforward")
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .returnResult()
        .getResponseBody();

    assertThat(defaults).isNotNull()
        .contains("mailSendForwardDefaultMapForUser")
        .contains("canRedirectToUsers")
        .contains(MessageComType.USER_ADMIN_ANNOUNCEMENT.name())
        .contains(MessageTargetType.INTERNAL_AND_EXTERNAL_MAIL.name());
  }

  @Order(2)
  @Test
  @DisplayName("Remove leftovers of an earlier run, then create a forwarding rule")
  void createMailSettingForward() {
    for (MailSettingForward existing : getSettingsOfUser()) {
      authenticatedClient(RestTestHelper.USER)
          .delete()
          .uri(RequestMappings.MAIL_SETTING_FORWARD_MAP + "/" + existing.getIdMailSettingForward())
          .exchange()
          .expectStatus().isNoContent();
    }

    MailSettingForward mailSettingForward = new MailSettingForward();
    mailSettingForward.setMessageComType(MessageComType.USER_ADMIN_ANNOUNCEMENT);
    mailSettingForward.setMessageTargetType(MessageTargetType.INTERNAL_AND_EXTERNAL_MAIL);

    MailSettingForward created = authenticatedClient(RestTestHelper.USER)
        .post()
        .uri(RequestMappings.MAIL_SETTING_FORWARD_MAP)
        .body(mailSettingForward)
        .exchange()
        .expectStatus().isOk()
        .expectBody(MailSettingForward.class)
        .returnResult()
        .getResponseBody();

    assertThat(created).isNotNull();
    assertThat(created.getIdMailSettingForward()).isPositive();
    assertThat(created.getMessageTargetType()).isEqualTo(MessageTargetType.INTERNAL_AND_EXTERNAL_MAIL);
    idMailSettingForward = created.getIdMailSettingForward();
  }

  @Order(3)
  @Test
  @DisplayName("The rule is changed to internal delivery only")
  void updateMailSettingForward() {
    MailSettingForward mailSettingForward = new MailSettingForward();
    mailSettingForward.setIdMailSettingForward(idMailSettingForward);
    mailSettingForward.setMessageComType(MessageComType.USER_ADMIN_ANNOUNCEMENT);
    mailSettingForward.setMessageTargetType(MessageTargetType.INTERNAL_MAIL);

    MailSettingForward updated = authenticatedClient(RestTestHelper.USER)
        .put()
        .uri(RequestMappings.MAIL_SETTING_FORWARD_MAP)
        .body(mailSettingForward)
        .exchange()
        .expectStatus().isOk()
        .expectBody(MailSettingForward.class)
        .returnResult()
        .getResponseBody();

    assertThat(updated).isNotNull();
    assertThat(updated.getIdMailSettingForward()).isEqualTo(idMailSettingForward);
    assertThat(updated.getMessageTargetType()).isEqualTo(MessageTargetType.INTERNAL_MAIL);
  }

  @Order(4)
  @Test
  @DisplayName("The rule is listed for its own user and removed again")
  void listAndDeleteMailSettingForward() {
    assertThat(getSettingsOfUser()).anyMatch(s -> idMailSettingForward.equals(s.getIdMailSettingForward()));

    authenticatedClient(RestTestHelper.USER)
        .delete()
        .uri(RequestMappings.MAIL_SETTING_FORWARD_MAP + "/" + idMailSettingForward)
        .exchange()
        .expectStatus().isNoContent();

    assertThat(getSettingsOfUser()).noneMatch(s -> idMailSettingForward.equals(s.getIdMailSettingForward()));
  }

  private List<MailSettingForward> getSettingsOfUser() {
    List<MailSettingForward> settings = authenticatedClient(RestTestHelper.USER)
        .get()
        .uri(RequestMappings.MAIL_SETTING_FORWARD_MAP + "/user")
        .exchange()
        .expectStatus().isOk()
        .expectBody(new ParameterizedTypeReference<List<MailSettingForward>>() {})
        .returnResult()
        .getResponseBody();
    assertThat(settings).isNotNull();
    return settings;
  }
}
