package grafiosch.gtnet.peer;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
 * The daily request budget of GitHub issue #234, which only exists between two real instances.
 *
 * Both directions are exercised and they are deliberately separated, because in a well-behaved pair only one of them is
 * ever reached: the requester counts what it sends and stops on its own once the limit the peer published is spent, so
 * the answering side's refusal is what protects an instance against a peer whose copy of that limit is too high. This
 * class produces that disagreement on purpose by raising peer A's stored copy of peer B's limit.
 *
 * Ping stays exempt throughout, otherwise a peer could spend itself out of reach of the status check.
 */
@TestMethodOrder(OrderAnnotation.class)
class GTNetPeerDailyRequestLimitTest {

  private static final String REQUEST_CODE = "GT_NET_DATA_REQUEST_SEL_RR_C";
  private static final String ACCEPT_CODE = "GT_NET_DATA_REQUEST_ACCEPT_S";
  private static final String LIMIT_CODE = "GT_NET_DAILY_REQUEST_LIMIT_EXCEEDED_S";
  private static final String ENTITY_KIND = "INTEGRATION_STREAM";
  /** Priority used only by this class, so a leftover row can be told apart from the other peer tests. */
  private static final int PRIORITY = 43;

  private static String jwtA;
  private static String jwtB;
  private static int remoteB;
  private static int remoteA;
  private static int baselineMessageIdA;
  private static int baselineMessageIdB;
  private static ObjectNode originalOwnB;

  @BeforeAll
  static void connect() throws Exception {
    jwtA = GTNetPeerTestSupport.loginAdmin(GTNetPeerTestSupport.PEER_A);
    jwtB = GTNetPeerTestSupport.loginAdmin(GTNetPeerTestSupport.PEER_B);
    remoteB = GTNetPeerTestSupport.remoteId(GTNetPeerTestSupport.readGTNet(GTNetPeerTestSupport.PEER_A, jwtA),
        requiredEnv("GTNET_PEER_B_OWN_URL"));
    remoteA = GTNetPeerTestSupport.remoteId(GTNetPeerTestSupport.readGTNet(GTNetPeerTestSupport.PEER_B, jwtB),
        requiredEnv("GTNET_PEER_A_OWN_URL"));
    baselineMessageIdA = newestMessageId(GTNetPeerTestSupport.PEER_A, jwtA, remoteB);
    baselineMessageIdB = newestMessageId(GTNetPeerTestSupport.PEER_B, jwtB, remoteA);
    originalOwnB = ownEntry(GTNetPeerTestSupport.PEER_B, jwtB).deepCopy();
    deleteRulesOfThisTest();
    createAcceptRule();
  }

  /**
   * Restores peer B's published limit before anything else. A budget left exhausted would refuse every request of every
   * later test for the rest of the UTC day, because the counters only roll over on a new day.
   */
  @AfterAll
  static void cleanUp() throws Exception {
    if (originalOwnB != null) {
      saveOwnEntry(GTNetPeerTestSupport.PEER_B, jwtB, originalOwnB);
    }
    deleteRulesOfThisTest();
    deleteMessagesAfter(GTNetPeerTestSupport.PEER_A, jwtA, remoteB, baselineMessageIdA);
    deleteMessagesAfter(GTNetPeerTestSupport.PEER_B, jwtB, remoteA, baselineMessageIdB);
  }

  @Test
  @Order(1)
  void aRequestThatStillFitsInTheBudgetIsAnsweredAndCounted() throws Exception {
    int before = chargedIncomingCount();
    publishLimitOfPeerB(before + 1);

    assertThat(sendDataRequest().path("messageCode").asString()).isEqualTo(ACCEPT_CODE);
    assertThat(chargedIncomingCount()).isEqualTo(before + 1);
  }

  @Test
  @Order(2)
  void theRequesterStopsSendingOnceThePublishedLimitIsSpent() throws Exception {
    // Peer A learned the lowered limit from the answer of the previous test and its own outgoing counter has reached
    // it, so this request never leaves peer A.
    int before = chargedIncomingCount();
    int newestBefore = newestMessageId(GTNetPeerTestSupport.PEER_A, jwtA, remoteB);

    submitDataRequest();

    assertThat(newestAnswerAfter(newestBefore)).as("no answer, because nothing was sent").isNull();
    assertThat(chargedIncomingCount()).as("peer B never saw the request").isEqualTo(before);
  }

  @Test
  @Order(3)
  void theAnsweringPeerRefusesARequesterWhoseCopyOfTheLimitIsTooHigh() throws Exception {
    // Disagreement on purpose: peer A believes it may still ask, peer B knows it may not.
    raiseStoredLimitOfRemoteB(originalOwnB.path("dailyRequestLimit").asInt(1000) + 1000);

    JsonNode refusal = sendDataRequest();
    assertThat(refusal.path("messageCode").asString()).isEqualTo(LIMIT_CODE);
    assertThat(refusal.path("message").asString()).contains("Daily request limit");
  }

  @Test
  @Order(4)
  void pingIsNeverChargedSoAnExhaustedPeerStaysReachable() throws Exception {
    var response = GTNetPeerTestSupport.postApi(GTNetPeerTestSupport.PEER_A, "/api/gtnet/" + remoteB + "/checkstatus",
        jwtA, "null");
    assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);

    assertThat(remoteEntry(GTNetPeerTestSupport.PEER_A, jwtA, remoteB).path("serverOnline").asString())
        .contains("SOS_ONLINE");
  }

  /** The number of requests peer B has charged to peer A today, 0 while no request of this day was charged. */
  private static int chargedIncomingCount() throws Exception {
    return remoteEntry(GTNetPeerTestSupport.PEER_B, jwtB, remoteA).path("gtNetConfig").path("dailyRequestLimitCount")
        .asInt(0);
  }

  private static void publishLimitOfPeerB(int dailyRequestLimit) throws Exception {
    ObjectNode ownB = ownEntry(GTNetPeerTestSupport.PEER_B, jwtB);
    ownB.put("dailyRequestLimit", dailyRequestLimit);
    saveOwnEntry(GTNetPeerTestSupport.PEER_B, jwtB, ownB);
  }

  /** Edits peer A's copy of peer B, which is what peer A's own outgoing guard measures against. */
  private static void raiseStoredLimitOfRemoteB(int dailyRequestLimit) throws Exception {
    ObjectNode storedRemote = ((ObjectNode) remoteEntry(GTNetPeerTestSupport.PEER_A, jwtA, remoteB)).deepCopy();
    storedRemote.put("dailyRequestLimit", dailyRequestLimit);
    var response = GTNetPeerTestSupport.putApi(GTNetPeerTestSupport.PEER_A, "/api/gtnet", jwtA,
        storedRemote.toString());
    assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
  }

  /** Sends a data request from peer A to peer B and returns the answer peer A stored for it. */
  private static JsonNode sendDataRequest() throws Exception {
    int before = newestMessageId(GTNetPeerTestSupport.PEER_A, jwtA, remoteB);
    submitDataRequest();
    JsonNode answer = newestAnswerAfter(before);
    assertThat(answer).as("peer A stored an answer for its data request").isNotNull();
    return answer;
  }

  private static void submitDataRequest() throws Exception {
    var response = GTNetPeerTestSupport.submit(GTNetPeerTestSupport.PEER_A, jwtA, remoteB, REQUEST_CODE,
        Map.of("entityKinds", ENTITY_KIND), "GTNet peer daily request limit probe");
    assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
  }

  private static JsonNode newestAnswerAfter(int baselineId) throws Exception {
    JsonNode answer = null;
    int newest = baselineId;
    for (JsonNode message : messages(GTNetPeerTestSupport.PEER_A, jwtA, remoteB)) {
      int id = message.path("idGtNetMessage").asInt();
      if (id > newest && !REQUEST_CODE.equals(message.path("messageCode").asString())) {
        newest = id;
        answer = message;
      }
    }
    return answer;
  }

  private static void createAcceptRule() throws Exception {
    String body = "{\"requestMsgCode\":\"" + REQUEST_CODE + "\",\"responseMsgCode\":\"" + ACCEPT_CODE
        + "\",\"priority\":" + PRIORITY + ",\"waitDaysApply\":0}";
    var response = GTNetPeerTestSupport.postApi(GTNetPeerTestSupport.PEER_B, "/api/gtnetmessageanswer", jwtB, body);
    assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
  }

  private static void deleteRulesOfThisTest() throws Exception {
    var response = GTNetPeerTestSupport.getApi(GTNetPeerTestSupport.PEER_B, "/api/gtnetmessageanswer", jwtB);
    assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
    for (JsonNode rule : GTNetPeerTestSupport.JSON.readTree(response.body())) {
      if (rule.path("priority").asInt() == PRIORITY) {
        GTNetPeerTestSupport.deleteApi(GTNetPeerTestSupport.PEER_B,
            "/api/gtnetmessageanswer/" + rule.path("idGtNetMessageAnswer").asInt(), jwtB);
      }
    }
  }

  private static JsonNode messages(URI peer, String jwt, int remoteId) throws Exception {
    var response = GTNetPeerTestSupport.getApi(peer, "/api/gtnet/messages/" + remoteId, jwt);
    assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
    return GTNetPeerTestSupport.JSON.readTree(response.body());
  }

  private static int newestMessageId(URI peer, String jwt, int remoteId) throws Exception {
    int newest = 0;
    for (JsonNode message : messages(peer, jwt, remoteId)) {
      newest = Math.max(newest, message.path("idGtNetMessage").asInt());
    }
    return newest;
  }

  private static void deleteMessagesAfter(URI peer, String jwt, int remoteId, int baselineId) throws Exception {
    List<Integer> ids = new ArrayList<>();
    for (JsonNode message : messages(peer, jwt, remoteId)) {
      if (message.path("idGtNetMessage").asInt() > baselineId) {
        ids.add(message.path("idGtNetMessage").asInt());
      }
    }
    if (!ids.isEmpty()) {
      GTNetPeerTestSupport.postApi(peer, "/api/gtnet/deletemessagebatch", jwt, ids.toString());
    }
  }

  private static ObjectNode ownEntry(URI peer, String jwt) throws Exception {
    JsonNode state = GTNetPeerTestSupport.readGTNet(peer, jwt);
    int ownId = state.path("gtNetMyEntryId").asInt();
    assertThat(ownId).as("own entry of %s", peer).isPositive();
    for (JsonNode entry : state.path("gtNetList")) {
      if (entry.path("idGtNet").asInt() == ownId) {
        return (ObjectNode) entry;
      }
    }
    throw new IllegalStateException("Own entry " + ownId + " not in the list of " + peer);
  }

  private static void saveOwnEntry(URI peer, String jwt, ObjectNode ownEntry) throws Exception {
    var response = GTNetPeerTestSupport.putApi(peer, "/api/gtnet", jwt, ownEntry.toString());
    assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
  }

  private static JsonNode remoteEntry(URI peer, String jwt, int idGtNet) throws Exception {
    for (JsonNode entry : GTNetPeerTestSupport.readGTNet(peer, jwt).path("gtNetList")) {
      if (entry.path("idGtNet").asInt() == idGtNet) {
        return entry;
      }
    }
    throw new IllegalStateException("Remote entry " + idGtNet + " not found at " + peer);
  }

  private static String requiredEnv(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " is required; run this suite through e2eTest --gtnet");
    }
    return value;
  }
}
