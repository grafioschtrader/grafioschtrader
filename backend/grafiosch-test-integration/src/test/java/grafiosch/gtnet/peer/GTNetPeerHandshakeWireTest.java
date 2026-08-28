package grafiosch.gtnet.peer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import grafiosch.test.gtnet.GTNetPeerTestSupport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Drives peer B over the raw wire as a synthetic third peer.
 *
 * A synthetic peer is what makes the token assertions possible at all: both tokens travel as message parameters of the
 * handshake and its reply, while GTNetConfig.tokenThis and tokenRemote are annotated JsonIgnore on the entity, so a
 * client that did not perform the handshake itself can never learn them. Nothing calls back to the synthetic address -
 * BaseDataClient.getActuatorInfo only fires on the very first gt_net insert - so a documentation address is safe here.
 */
@TestMethodOrder(OrderAnnotation.class)
class GTNetPeerHandshakeWireTest {

  /** RFC 2606 reserved name; peer B stores it as an ordinary remote entry and never dials it. */
  private static final String SYNTHETIC_DOMAIN = "http://synthetic-peer.example:8099";
  private static final byte PING = 0;
  private static final byte FIRST_HANDSHAKE = 1;
  private static final byte FIRST_HANDSHAKE_ACCEPT = 2;
  private static final byte FIRST_HANDSHAKE_REJECT_NOT_IN_LIST = 4;
  private static final byte TOKEN_REFRESH = 5;
  private static final byte TOKEN_REFRESH_ACCEPT = 6;
  /** Registered by IntegrationTestMessageCodeConfig under the e2e profile, deliberately without a handler. */
  private static final byte NO_HANDLER_CODE = 120;
  private static final byte ERROR = 23;
  /** A second RFC 2606 reserved name, used to make the envelope and the payload disagree. */
  private static final String OTHER_DOMAIN = "http://other-peer.example:8099";

  private static String jwtB;
  private static int envelopeIds = 5000;
  /** The token peer B expects from us; replaced by the refresh. */
  private static String tokenForPeerB;
  private static String supersededTokenForPeerB;

  @BeforeAll
  static void connect() throws Exception {
    jwtB = GTNetPeerTestSupport.loginAdmin(GTNetPeerTestSupport.PEER_B);
    deleteSyntheticPeer();
  }

  @AfterAll
  static void removeSyntheticPeer() throws Exception {
    setAllowServerCreation(true);
    deleteSyntheticPeer();
  }

  @Test
  @Order(1)
  void anUnknownPeerIsRejectedWhenServerCreationIsOff() throws Exception {
    setAllowServerCreation(false);

    JsonNode reply = send(handshakeEnvelope(UUID.randomUUID().toString()), null);

    assertThat(reply.path("messageCode").asInt()).isEqualTo(FIRST_HANDSHAKE_REJECT_NOT_IN_LIST);
    assertThat(GTNetPeerTestSupport.readGTNet(GTNetPeerTestSupport.PEER_B, jwtB).toString())
        .doesNotContain(SYNTHETIC_DOMAIN);
  }

  @Test
  @Order(2)
  void anAcceptedHandshakeReturnsATokenAndLeaksNoneInThePayload() throws Exception {
    setAllowServerCreation(true);
    String ourTokenForPeerB = UUID.randomUUID().toString();

    JsonNode reply = send(handshakeEnvelope(ourTokenForPeerB), null);

    assertThat(reply.path("messageCode").asInt()).isEqualTo(FIRST_HANDSHAKE_ACCEPT);
    tokenForPeerB = reply.path("gtNetMessageParamMap").path("tokenThis").path("paramValue").asString();
    assertThat(tokenForPeerB).isNotBlank();

    // The accept payload is the GTNet entity of the answering peer, with its EAGER gtNetConfig. Neither token field
    // may appear there - a token is carried as a message parameter, and only towards the peer it was issued to.
    JsonNode payload = reply.path("payload");
    assertThat(payload.isMissingNode()).isFalse();
    assertThat(payload.toString()).doesNotContain("tokenThis").doesNotContain("tokenRemote");
  }

  @Test
  @Order(3)
  void theIssuedTokenAuthenticatesAndAWrongOneDoesNot() throws Exception {
    assertThat(GTNetPeerTestSupport
        .postJson(GTNetPeerTestSupport.PEER_B, "/m2m/gtnet", tokenForPeerB, envelope(PING).toString()).statusCode())
            .isEqualTo(200);

    var refused = GTNetPeerTestSupport.postJson(GTNetPeerTestSupport.PEER_B, "/m2m/gtnet", UUID.randomUUID().toString(),
        envelope(PING).toString());
    assertUnauthorized(refused.statusCode(), refused.body());
  }

  /**
   * A refresh installs a new token and keeps the one it replaced usable for the overlap window.
   *
   * <p>
   * The answerer commits the replacement while it is still handling the request, so the initiator only learns of it
   * when the response arrives. Rejecting the old token immediately means a response lost in transit locks the peer out
   * for good, and the refresh that would repair it is itself authenticated. Both tokens therefore work until the window
   * passes.
   * </p>
   */
  @Test
  @Order(4)
  void aRefreshedTokenSupersedesTheOldOneButLeavesItUsableForTheOverlap() throws Exception {
    supersededTokenForPeerB = tokenForPeerB;

    ObjectNode refresh = envelope(TOKEN_REFRESH);
    refresh.putObject("gtNetMessageParamMap").putObject("tokenThis").put("paramValue", UUID.randomUUID().toString());
    JsonNode reply = send(refresh, supersededTokenForPeerB);

    assertThat(reply.path("messageCode").asInt()).isEqualTo(TOKEN_REFRESH_ACCEPT);
    tokenForPeerB = reply.path("gtNetMessageParamMap").path("tokenThis").path("paramValue").asString();
    assertThat(tokenForPeerB).isNotBlank().isNotEqualTo(supersededTokenForPeerB);

    assertThat(GTNetPeerTestSupport
        .postJson(GTNetPeerTestSupport.PEER_B, "/m2m/gtnet", supersededTokenForPeerB, envelope(PING).toString())
        .statusCode()).as("the replaced token stays usable inside the overlap window").isEqualTo(200);

    assertThat(GTNetPeerTestSupport
        .postJson(GTNetPeerTestSupport.PEER_B, "/m2m/gtnet", tokenForPeerB, envelope(PING).toString()).statusCode())
            .isEqualTo(200);

    var withAnUnrelatedToken = GTNetPeerTestSupport.postJson(GTNetPeerTestSupport.PEER_B, "/m2m/gtnet",
        UUID.randomUUID().toString(), envelope(PING).toString());
    assertUnauthorized(withAnUnrelatedToken.statusCode(), withAnUnrelatedToken.body());
  }

  /**
   * A second refresh inside the same window does not evict the token the peer may still be presenting.
   *
   * <p>
   * This is the case the overlap exists for: a peer that saw neither response is still on the original token. If a
   * retry pushed the first rotation's token into the overlap slot, that peer would be locked out by the very mechanism
   * meant to rescue it.
   * </p>
   */
  @Test
  @Order(5)
  void aSecondRefreshKeepsTheOldestStillValidPredecessor() throws Exception {
    String beforeSecondRotation = tokenForPeerB;

    ObjectNode refresh = envelope(TOKEN_REFRESH);
    refresh.putObject("gtNetMessageParamMap").putObject("tokenThis").put("paramValue", UUID.randomUUID().toString());
    JsonNode reply = send(refresh, beforeSecondRotation);

    assertThat(reply.path("messageCode").asInt()).isEqualTo(TOKEN_REFRESH_ACCEPT);
    tokenForPeerB = reply.path("gtNetMessageParamMap").path("tokenThis").path("paramValue").asString();

    assertThat(GTNetPeerTestSupport
        .postJson(GTNetPeerTestSupport.PEER_B, "/m2m/gtnet", supersededTokenForPeerB, envelope(PING).toString())
        .statusCode()).as("the oldest still valid predecessor survives a second rotation").isEqualTo(200);
    assertThat(GTNetPeerTestSupport
        .postJson(GTNetPeerTestSupport.PEER_B, "/m2m/gtnet", tokenForPeerB, envelope(PING).toString()).statusCode())
            .isEqualTo(200);
  }

  @Test
  @Order(6)
  void aRegisteredCodeWithoutAHandlerIsAnsweredWithNoHandler() throws Exception {
    var response = GTNetPeerTestSupport.postJson(GTNetPeerTestSupport.PEER_B, "/m2m/gtnet", tokenForPeerB,
        envelope(NO_HANDLER_CODE).toString());

    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    assertThat(response.body()).contains("NO_HANDLER");
  }

  @Test
  @Order(7)
  void aHandshakeWhoseEnvelopeAndPayloadDisagreeIsRefused() throws Exception {
    ObjectNode handshake = handshakeEnvelope(UUID.randomUUID().toString());
    ((ObjectNode) handshake.get("payload")).put("domainRemoteName", OTHER_DOMAIN);

    JsonNode reply = send(handshake, null);

    assertThat(reply.path("messageCode").asInt()).isEqualTo(ERROR);
    assertThat(reply.path("errorMsgCode").asString()).isEqualTo("DOMAIN_MISMATCH");
    // Neither of the two names may have been created: the handshake is unauthenticated, so a caller that cannot even
    // agree with itself about who it is must not leave a peer entry behind.
    String gtNet = GTNetPeerTestSupport.readGTNet(GTNetPeerTestSupport.PEER_B, jwtB).toString();
    assertThat(gtNet).doesNotContain(OTHER_DOMAIN);
  }

  @Test
  @Order(8)
  void anUnauthenticatedHandshakeCannotReplaceAnEstablishedRelationship() throws Exception {
    ObjectNode takeover = handshakeEnvelope(UUID.randomUUID().toString());

    JsonNode reply = send(takeover, null);

    assertThat(reply.path("messageCode").asInt()).isEqualTo(ERROR);
    assertThat(reply.path("errorMsgCode").asString()).isEqualTo("HANDSHAKE_ALREADY_ESTABLISHED");
    // The token issued by the accepted handshake still works, so the takeover attempt changed nothing. Were the
    // handshake allowed to mint a fresh pair, this ping would fail and the legitimate peer would be locked out.
    assertThat(GTNetPeerTestSupport
        .postJson(GTNetPeerTestSupport.PEER_B, "/m2m/gtnet", tokenForPeerB, envelope(PING).toString()).statusCode())
            .isEqualTo(200);
  }

  /**
   * A missing, unknown or superseded token is refused by the security layer before the protocol runs, so the answer is
   * an HTTP error with an ErrorWrapper body and never an error envelope.
   *
   * @param statusCode the HTTP status the peer answered with
   * @param body       the response body, used as the assertion description
   */
  private static void assertUnauthorized(int statusCode, String body) {
    assertThat(statusCode).as(body).isEqualTo(401);
    assertThat(body).doesNotContain("replyToSourceId");
  }

  private static JsonNode send(ObjectNode envelope, String token) throws Exception {
    var response = GTNetPeerTestSupport.postJson(GTNetPeerTestSupport.PEER_B, "/m2m/gtnet", token, envelope.toString());
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    return GTNetPeerTestSupport.JSON.readTree(response.body());
  }

  private static ObjectNode envelope(byte messageCode) {
    ObjectNode envelope = GTNetPeerTestSupport.JSON.createObjectNode();
    envelope.put("sourceDomain", SYNTHETIC_DOMAIN);
    envelope.put("idSourceGtNetMessage", ++envelopeIds);
    envelope.put("timestamp", LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString());
    envelope.put("messageCode", messageCode);
    envelope.put("serverBusy", false);
    envelope.put("visibility", 0);
    return envelope;
  }

  /** The handshake carries our token as a parameter and our whole GTNet entity as the payload. */
  private static ObjectNode handshakeEnvelope(String ourTokenForPeerB) {
    ObjectNode envelope = envelope(FIRST_HANDSHAKE);
    envelope.putObject("gtNetMessageParamMap").putObject("tokenThis").put("paramValue", ourTokenForPeerB);
    ObjectNode payload = envelope.putObject("payload");
    payload.put("domainRemoteName", SYNTHETIC_DOMAIN);
    payload.put("timeZone", "UTC");
    payload.put("spreadCapability", false);
    payload.put("dailyRequestLimit", 100);
    payload.put("serverBusy", false);
    payload.put("allowServerCreation", false);
    payload.putArray("gtNetEntities");
    return envelope;
  }

  private static void setAllowServerCreation(boolean allow) throws Exception {
    JsonNode state = GTNetPeerTestSupport.readGTNet(GTNetPeerTestSupport.PEER_B, jwtB);
    int ownId = state.path("gtNetMyEntryId").asInt();
    for (JsonNode entry : state.path("gtNetList")) {
      if (entry.path("idGtNet").asInt() == ownId) {
        ObjectNode ownEntry = ((ObjectNode) entry).deepCopy();
        ownEntry.put("allowServerCreation", allow);
        var response = GTNetPeerTestSupport.putApi(GTNetPeerTestSupport.PEER_B, "/api/gtnet", jwtB,
            ownEntry.toString());
        assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
        return;
      }
    }
    throw new IllegalStateException("Peer B has no own entry");
  }

  /**
   * Removes the synthetic peer including its messages. Its handshake is a request-response pair answered immediately,
   * so nothing blocks the delete and the messages cascade with the row.
   */
  private static void deleteSyntheticPeer() throws Exception {
    for (JsonNode entry : GTNetPeerTestSupport.readGTNet(GTNetPeerTestSupport.PEER_B, jwtB).path("gtNetList")) {
      if (SYNTHETIC_DOMAIN.equals(entry.path("domainRemoteName").asString())) {
        GTNetPeerTestSupport.deleteApi(GTNetPeerTestSupport.PEER_B, "/api/gtnet/" + entry.path("idGtNet").asInt(),
            jwtB);
        return;
      }
    }
  }
}
