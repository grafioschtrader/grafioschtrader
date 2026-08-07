package grafiosch.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;

import com.jayway.jsonpath.JsonPath;

import grafiosch.types.ReplyToRolePrivateType;

/**
 * Round trip through the internal messaging system of {@code grafiosch-server-base}: the admin sends a message to a
 * user, both sides see it in their own folder, the recipient marks it as read and the sender hides it again.
 *
 * <p>
 * Requests and responses are handled as JSON rather than as {@code MailSendRecv} / {@code MailInboxWithSend}
 * instances, because neither type round trips through a Java client:
 * <ul>
 * <li>{@code MailSendRecv.getSendRecv()} writes the persisted char ({@code "S"} / {@code "R"}), while the only creator
 * Jackson finds takes a {@code SendRecvType} and therefore expects the enum name — so the entity can neither be sent
 * nor read back as itself.</li>
 * <li>{@code MailInboxWithSend.mailSendRecvList} holds {@code MailSendRecvDTO}, a Spring Data interface projection,
 * which has no constructor to deserialize into.</li>
 * </ul>
 * The frontend is a JSON client and is unaffected by both; asserting on the payload is therefore also the more
 * faithful contract test. The send direction is server side state and is deliberately not sent, exactly as the mail
 * dialog of the frontend leaves it unset.
 *
 * <p>
 * This is the JUnit counterpart of the browser spec {@code frontend/e2e/lib/010-mail.spec.ts}, which drives the same
 * endpoints through the reusable UI components.
 */
@TestMethodOrder(OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
class MailSendRecvResourceTest extends BaseIntegrationTest {

  private static final String SUBJECT = "Integration suite message";
  private static final String BODY = "Sent by MailSendRecvResourceTest";

  private Integer idSentMail;

  @BeforeAll
  void setUpUserToken() {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
  }

  @Order(1)
  @Test
  @DisplayName("Admin sends an internal message to user 'user'")
  void sendMessageToUser() {
    Map<String, Object> mailSendRecv = new LinkedHashMap<>();
    mailSendRecv.put("idUserFrom", RestTestHelper.getUserByNickname(RestTestHelper.ADMIN).idUser);
    mailSendRecv.put("idUserTo", RestTestHelper.getUserByNickname(RestTestHelper.USER).idUser);
    mailSendRecv.put("subject", SUBJECT);
    mailSendRecv.put("message", BODY);
    mailSendRecv.put("replyToRolePrivate", ReplyToRolePrivateType.REPLY_NORMAL.name());

    String created = authenticatedClient(RestTestHelper.ADMIN)
        .post()
        .uri(RequestMappings.MAIL_SEMD_RECV_MAP)
        .body(mailSendRecv)
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .returnResult()
        .getResponseBody();

    assertThat(created).isNotNull();
    assertThat(JsonPath.<String>read(created, "$.subject")).isEqualTo(SUBJECT);
    idSentMail = JsonPath.read(created, "$.idMailSendRecv");
    assertThat(idSentMail).isPositive();
  }

  @Order(2)
  @Test
  @DisplayName("Sender finds the message in the sent folder")
  void senderSeesMessage() {
    assertThat(subjectsOf(RestTestHelper.ADMIN, "S")).contains(SUBJECT);
  }

  @Order(3)
  @Test
  @DisplayName("Recipient receives the message and marks it as read")
  void recipientReadsMessage() {
    String inbox = getInbox(RestTestHelper.USER);
    assertThat(JsonPath.<List<String>>read(inbox, "$.mailSendRecvList[?(@.sendRecv=='R')].subject"))
        .contains(SUBJECT);

    List<Integer> received = JsonPath.read(inbox,
        "$.mailSendRecvList[?(@.subject=='" + SUBJECT + "')].idMailSendRecv");
    assertThat(received).isNotEmpty();

    authenticatedClient(RestTestHelper.USER)
        .post()
        .uri(RequestMappings.MAIL_SEMD_RECV_MAP + "/" + received.get(0) + "/markforread")
        .exchange()
        .expectStatus().isOk();

    assertThat(JsonPath.<List<Boolean>>read(getInbox(RestTestHelper.USER),
        "$.mailSendRecvList[?(@.subject=='" + SUBJECT + "')].hasBeenRead")).containsOnly(Boolean.TRUE);
  }

  @Order(4)
  @Test
  @DisplayName("Sender hides the message again, so the class can be rerun against the same database")
  void senderHidesMessage() {
    authenticatedClient(RestTestHelper.ADMIN)
        .delete()
        .uri(RequestMappings.MAIL_SEMD_RECV_MAP + "/" + idSentMail)
        .exchange()
        .expectStatus().isNoContent();

    assertThat(JsonPath.<List<Integer>>read(getInbox(RestTestHelper.ADMIN), "$.mailSendRecvList[*].idMailSendRecv"))
        .doesNotContain(idSentMail);
  }

  private List<String> subjectsOf(String nickname, String sendRecv) {
    return JsonPath.read(getInbox(nickname), "$.mailSendRecvList[?(@.sendRecv=='" + sendRecv + "')].subject");
  }

  private String getInbox(String nickname) {
    String inbox = authenticatedClient(nickname)
        .get()
        .uri(RequestMappings.MAIL_SEMD_RECV_MAP)
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .returnResult()
        .getResponseBody();
    assertThat(inbox).isNotNull();
    return inbox;
  }
}
