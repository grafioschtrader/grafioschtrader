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

/**
 * How peer B decides what to answer a data request with.
 *
 * Three rules govern it and none of them is reachable without a second real peer: GTNetMessageAnswer rows are evaluated
 * in ascending priority and the first match wins; a response that carried a waitDaysApply opens a cooling-off window in
 * which the dispatcher repeats that response without re-evaluating anything; and a request for which neither a rule nor
 * a handler default exists is persisted and merely acknowledged, waiting for an administrator.
 */
@TestMethodOrder(OrderAnnotation.class)
class GTNetPeerResponseRuleTest {

  private static final String REQUEST_CODE = "GT_NET_DATA_REQUEST_SEL_RR_C";
  private static final String ACCEPT_CODE = "GT_NET_DATA_REQUEST_ACCEPT_S";
  private static final String REJECT_CODE = "GT_NET_DATA_REQUEST_REJECTED_S";
  private static final String ENTITY_KIND = "INTEGRATION_STREAM";
  /** Priorities used only by this class, so a leftover row of another class can be told apart. */
  private static final int LOW_PRIORITY = 41;
  private static final int HIGH_PRIORITY = 42;

  private static String jwtA;
  private static String jwtB;
  private static int remoteB;
  private static int remoteA;
  private static int baselineMessageIdA;
  private static int baselineMessageIdB;

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
    deleteRulesOfThisTest();
  }

  /**
   * Removes the rules and every message this class produced. Deleting the responses matters beyond tidiness: a response
   * carrying waitDaysApply is what keeps a cooling-off window open, so leaving it behind would make every later data
   * request of this peer pair answer out of the cooling-off branch.
   */
  @AfterAll
  static void cleanUp() throws Exception {
    deleteRulesOfThisTest();
    deleteMessagesAfter(GTNetPeerTestSupport.PEER_A, jwtA, remoteB, baselineMessageIdA);
    deleteMessagesAfter(GTNetPeerTestSupport.PEER_B, jwtB, remoteA, baselineMessageIdB);
  }

  @Test
  @Order(1)
  void theLowerPriorityRuleDecides() throws Exception {
    createRule(ACCEPT_CODE, LOW_PRIORITY, 0);
    createRule(REJECT_CODE, HIGH_PRIORITY, 0);

    assertThat(sendDataRequest()).isEqualTo(ACCEPT_CODE);

    deleteRulesOfThisTest();
  }

  @Test
  @Order(2)
  void theOrderIsPriorityAndNotCreationOrder() throws Exception {
    createRule(REJECT_CODE, LOW_PRIORITY, 0);
    createRule(ACCEPT_CODE, HIGH_PRIORITY, 0);

    assertThat(sendDataRequest()).isEqualTo(REJECT_CODE);

    deleteRulesOfThisTest();
  }

  @Test
  @Order(3)
  void aRequestWithoutRuleOrDefaultIsOnlyAcknowledgedAndWaitsForAnAdministrator() throws Exception {
    deleteRulesOfThisTest();

    // The dispatcher answers AwaitingManualResponse with a plain ack, which is a ping-coded answer and therefore
    // clearly distinguishable from a decision.
    assertThat(sendDataRequest()).isEqualTo("GT_NET_PING");

    JsonNode pending = newestReceivedRequest(GTNetPeerTestSupport.PEER_B, jwtB, remoteA);
    assertThat(pending).isNotNull();
    assertThat(pending.path("messageCode").asText()).isEqualTo(REQUEST_CODE);

    // Answering it is the administrator action the ack was waiting for.
    var answer = GTNetPeerTestSupport.JSON.createObjectNode();
    answer.put("idGTNetTargetDomain", remoteA);
    answer.put("replyTo", pending.path("idGtNetMessage").asInt());
    answer.put("messageCode", ACCEPT_CODE);
    answer.put("message", "GTNet peer manual approval");
    answer.putObject("gtNetMessageParamMap");
    var response = GTNetPeerTestSupport.postApi(GTNetPeerTestSupport.PEER_B, "/api/gtnet/submitmsg", jwtB,
        answer.toString());
    assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
    assertThat(newestReceivedRequest(GTNetPeerTestSupport.PEER_B, jwtB, remoteA)).isNull();
  }

  @Test
  @Order(4)
  void aRejectionWithWaitDaysRefusesTheNextRequestWithoutEvaluatingTheRules() throws Exception {
    createRule(REJECT_CODE, LOW_PRIORITY, 3);

    assertThat(sendDataRequest()).isEqualTo(REJECT_CODE);

    // Second attempt inside the window: the dispatcher repeats the rejection before any handler runs, and says how
    // much of the window is left.
    JsonNode refusal = sendDataRequestMessage();
    assertThat(refusal.path("messageCode").asText()).isEqualTo(REJECT_CODE);
    assertThat(refusal.path("message").asText()).contains("Cooling-off period active");
  }

  /**
   * Sends a data request from peer A to peer B and reports the code peer A received back.
   *
   * @return the message code name of the answer peer A stored for this request
   */
  private static String sendDataRequest() throws Exception {
    return sendDataRequestMessage().path("messageCode").asText();
  }

  private static JsonNode sendDataRequestMessage() throws Exception {
    int before = newestMessageId(GTNetPeerTestSupport.PEER_A, jwtA, remoteB);
    var response = GTNetPeerTestSupport.submit(GTNetPeerTestSupport.PEER_A, jwtA, remoteB, REQUEST_CODE,
        Map.of("entityKinds", ENTITY_KIND), "GTNet peer response rule probe");
    assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);

    JsonNode answer = null;
    int newest = before;
    for (JsonNode message : messages(GTNetPeerTestSupport.PEER_A, jwtA, remoteB)) {
      int id = message.path("idGtNetMessage").asInt();
      if (id > newest && !REQUEST_CODE.equals(message.path("messageCode").asText())) {
        newest = id;
        answer = message;
      }
    }
    assertThat(answer).as("peer A stored an answer for its data request").isNotNull();
    return answer;
  }

  /**
   * @return the newest received request that has no answer yet, or null when every request was answered
   */
  private static JsonNode newestReceivedRequest(URI peer, String jwt, int remoteId) throws Exception {
    List<Integer> answered = new ArrayList<>();
    JsonNode candidate = null;
    for (JsonNode message : messages(peer, jwt, remoteId)) {
      if (message.hasNonNull("replyTo")) {
        answered.add(message.path("replyTo").asInt());
      }
    }
    for (JsonNode message : messages(peer, jwt, remoteId)) {
      if (REQUEST_CODE.equals(message.path("messageCode").asText())
          && "RECEIVED".equals(message.path("sendRecv").asText())
          && !answered.contains(message.path("idGtNetMessage").asInt())) {
        candidate = message;
      }
    }
    return candidate;
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

  /** Deletes what this class added; messages the protocol still considers pending are simply left in place. */
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

  private static void createRule(String responseCode, int priority, int waitDays) throws Exception {
    String body = "{\"requestMsgCode\":\"" + REQUEST_CODE + "\",\"responseMsgCode\":\"" + responseCode
        + "\",\"priority\":" + priority + ",\"waitDaysApply\":" + waitDays + "}";
    var response = GTNetPeerTestSupport.postApi(GTNetPeerTestSupport.PEER_B, "/api/gtnetmessageanswer", jwtB, body);
    assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
  }

  private static void deleteRulesOfThisTest() throws Exception {
    var response = GTNetPeerTestSupport.getApi(GTNetPeerTestSupport.PEER_B, "/api/gtnetmessageanswer", jwtB);
    assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
    for (JsonNode rule : GTNetPeerTestSupport.JSON.readTree(response.body())) {
      int priority = rule.path("priority").asInt();
      if (priority == LOW_PRIORITY || priority == HIGH_PRIORITY) {
        GTNetPeerTestSupport.deleteApi(GTNetPeerTestSupport.PEER_B,
            "/api/gtnetmessageanswer/" + rule.path("idGtNetMessageAnswer").asInt(), jwtB);
      }
    }
  }

  private static String requiredEnv(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " is required; run this suite through e2eTest --gtnet");
    }
    return value;
  }
}
