package grafiosch.gtnet.peer;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import grafiosch.test.gtnet.GTNetPeerTestSupport;
import grafiosch.test.gtnet.SyntheticPeer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Delivers the same envelope twice and checks that the second delivery changes nothing.
 *
 * <p>
 * Redelivery is a normal event rather than an attack: both delivery tasks re-send the same persisted message and the
 * envelope carries its id unchanged, so a retry after a lost response is byte-identical. Until the peer-scoped triple
 * was enforced, such a retry inserted a second row, repeated the handler's side effects and charged the daily budget
 * again.
 * </p>
 */
@TestMethodOrder(OrderAnnotation.class)
class GTNetPeerIdempotencyTest {

  /** RFC 2606 reserved name; peer B stores it as an ordinary remote entry and never dials it. */
  private static final String SYNTHETIC_DOMAIN = "http://duplicate-probe.example:8099";
  private static final byte TOKEN_REFRESH = 5;
  private static final byte TOKEN_REFRESH_ACCEPT = 6;
  private static final byte OFFLINE_ALL = 20;
  private static final byte ACK = 21;
  private static final URI PEER = GTNetPeerTestSupport.PEER_B;

  private static String jwtB;
  private static SyntheticPeer synthetic;

  @BeforeAll
  static void connect() throws Exception {
    jwtB = GTNetPeerTestSupport.loginAdmin(PEER);
    synthetic = SyntheticPeer.connect(PEER, SYNTHETIC_DOMAIN);
  }

  @AfterAll
  static void disconnect() throws Exception {
    if (synthetic != null) {
      synthetic.disconnect(jwtB);
    }
  }

  /**
   * A repeated request is answered from what the first delivery produced, so a token refresh whose response was lost
   * can be retried without rotating a second time.
   */
  @Test
  @Order(1)
  void answersARepeatedRequestWithTheAnswerItAlreadyGave() throws Exception {
    ObjectNode refresh = synthetic.newEnvelope(TOKEN_REFRESH);
    refresh.putObject("gtNetMessageParamMap").putObject("tokenThis").put("paramValue", UUID.randomUUID().toString());

    JsonNode first = synthetic.sendRaw(refresh);
    assertThat(first.path("messageCode").asInt()).as("answer was %s", first).isEqualTo(TOKEN_REFRESH_ACCEPT);
    String firstToken = first.path("gtNetMessageParamMap").path("tokenThis").path("paramValue").asString();
    assertThat(firstToken).isNotBlank();
    int rowsAfterFirst = messageCountOfSynthetic();

    // The same envelope object, so the retry is byte-identical the way a delivery task's retry is.
    JsonNode second = synthetic.sendRaw(refresh);

    assertThat(second.path("messageCode").asInt()).isEqualTo(TOKEN_REFRESH_ACCEPT);
    assertThat(second.path("gtNetMessageParamMap").path("tokenThis").path("paramValue").asString())
        .as("a repeated refresh must not rotate again").isEqualTo(firstToken);
    assertThat(messageCountOfSynthetic()).as("a redelivery must not insert a second row").isEqualTo(rowsAfterFirst);
  }

  /** A redelivered announcement is acknowledged as a recognized repeat instead of being applied a second time. */
  @Test
  @Order(2)
  void acknowledgesARedeliveredAnnouncementWithoutRepeatingIt() throws Exception {
    ObjectNode offline = synthetic.newEnvelope(OFFLINE_ALL);

    assertThat(synthetic.sendRaw(offline).path("messageCode").asInt()).isEqualTo(ACK);
    int rowsAfterFirst = messageCountOfSynthetic();

    JsonNode second = synthetic.sendRaw(offline);

    assertThat(second.path("messageCode").asInt()).isEqualTo(ACK);
    assertThat(second.path("errorMsgCode").asString()).isEqualTo("DUPLICATE_DELIVERY");
    assertThat(messageCountOfSynthetic()).as("a redelivery must not insert a second row").isEqualTo(rowsAfterFirst);
  }

  /** A fresh envelope of the same code is ordinary traffic and is processed normally. */
  @Test
  @Order(3)
  void stillProcessesANewDeliveryOfTheSameCode() throws Exception {
    int rowsBefore = messageCountOfSynthetic();

    JsonNode reply = synthetic.sendRaw(synthetic.newEnvelope(OFFLINE_ALL));

    assertThat(reply.path("messageCode").asInt()).isEqualTo(ACK);
    assertThat(reply.path("errorMsgCode").isNull() || reply.path("errorMsgCode").asString().isEmpty()).isTrue();
    assertThat(messageCountOfSynthetic()).isEqualTo(rowsBefore + 1);
  }

  /** How many messages peer B holds against this synthetic peer, read from the setup screen's own count map. */
  private static int messageCountOfSynthetic() throws Exception {
    JsonNode state = GTNetPeerTestSupport.readGTNet(PEER, jwtB);
    for (JsonNode entry : state.path("gtNetList")) {
      if (SYNTHETIC_DOMAIN.equals(entry.path("domainRemoteName").asString())) {
        return state.path("gtNetMessageCountMap").path(String.valueOf(entry.path("idGtNet").asInt())).asInt(0);
      }
    }
    return 0;
  }
}
