package grafiosch.gtnet.peer;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import grafiosch.test.gtnet.GTNetPeerTestSupport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Real authenticated protocol traffic between the runner-owned peers. */
@TestMethodOrder(OrderAnnotation.class)
class GTNetPeerAuthenticatedProtocolTest {

  private static String jwtA;
  private static String jwtB;
  private static int remoteB;
  private static int remoteA;
  private static int ownA;

  @BeforeAll
  static void connect() throws Exception {
    jwtA = GTNetPeerTestSupport.loginAdmin(GTNetPeerTestSupport.PEER_A);
    jwtB = GTNetPeerTestSupport.loginAdmin(GTNetPeerTestSupport.PEER_B);
    JsonNode stateA = GTNetPeerTestSupport.readGTNet(GTNetPeerTestSupport.PEER_A, jwtA);
    ownA = stateA.path("gtNetMyEntryId").asInt();
    remoteB = GTNetPeerTestSupport.remoteId(stateA, requiredEnv("GTNET_PEER_B_OWN_URL"));
    remoteA = GTNetPeerTestSupport.remoteId(GTNetPeerTestSupport.readGTNet(GTNetPeerTestSupport.PEER_B, jwtB),
        requiredEnv("GTNET_PEER_A_OWN_URL"));
  }

  @Test
  @Order(1)
  void pingRoundTripUpdatesOnlineStateAndTokensStayPrivate() throws Exception {
    var response = GTNetPeerTestSupport.postApi(GTNetPeerTestSupport.PEER_A, "/api/gtnet/" + remoteB + "/checkstatus",
        jwtA, "null");
    assertOk(response.statusCode(), response.body());
    assertThat(response.body()).containsAnyOf("SOS_ONLINE", "\"serverOnline\":1");
    String publicState = GTNetPeerTestSupport.readGTNet(GTNetPeerTestSupport.PEER_A, jwtA).toString();
    assertThat(publicState).doesNotContain("tokenThis", "tokenRemote");
  }

  @Test
  @Order(2)
  void tokenRefreshAcceptAndRejectKeepTheEstablishedConnectionUsable() throws Exception {
    var accepted = GTNetPeerTestSupport.submit(GTNetPeerTestSupport.PEER_A, jwtA, remoteB,
        "GT_NET_TOKEN_REFRESH_SEL_RR_C", Map.of(), "GTNet peer token refresh");
    assertOk(accepted.statusCode(), accepted.body());

    int ruleId = createRule("GT_NET_TOKEN_REFRESH_SEL_RR_C", "GT_NET_TOKEN_REFRESH_REJECTED_S", 21, 0);
    try {
      var rejected = GTNetPeerTestSupport.submit(GTNetPeerTestSupport.PEER_A, jwtA, remoteB,
          "GT_NET_TOKEN_REFRESH_SEL_RR_C", Map.of(), "GTNet peer rejected token refresh");
      assertOk(rejected.statusCode(), rejected.body());
    } finally {
      deleteRule(ruleId);
    }
    assertOk(
        GTNetPeerTestSupport
            .postApi(GTNetPeerTestSupport.PEER_A, "/api/gtnet/" + remoteB + "/checkstatus", jwtA, "null").statusCode(),
        "ping after token refresh");
    // The rejecting peer keeps the token of the accepted refresh. Peer A rotated again before the rejection arrived,
    // and inside the overlap window that rotation drops exactly this token, so the reverse direction is what breaks.
    var reverse = GTNetPeerTestSupport.postApi(GTNetPeerTestSupport.PEER_B, "/api/gtnet/" + remoteA + "/checkstatus",
        jwtB, "null");
    assertOk(reverse.statusCode(), reverse.body());
    assertThat(reverse.body()).as("peer B still authenticates at peer A").containsAnyOf("SOS_ONLINE",
        "\"serverOnline\":1");
  }

  @Test
  @Order(3)
  void dataRequestAcceptAndRevokeUseTheRegisteredLibraryKind() throws Exception {
    int ruleId = createRule("GT_NET_DATA_REQUEST_SEL_RR_C", "GT_NET_DATA_REQUEST_ACCEPT_S", 22, 0);
    try {
      var accept = GTNetPeerTestSupport.submit(GTNetPeerTestSupport.PEER_A, jwtA, remoteB,
          "GT_NET_DATA_REQUEST_SEL_RR_C", Map.of("entityKinds", "INTEGRATION_STREAM"), "GTNet peer data request");
      assertOk(accept.statusCode(), accept.body());
    } finally {
      deleteRule(ruleId);
    }
    assertThat(GTNetPeerTestSupport.readGTNet(GTNetPeerTestSupport.PEER_A, jwtA).toString())
        .contains("INTEGRATION_STREAM");
  }

  @Test
  @Order(4)
  void serverListRequestAndRevokeTraverseTheWire() throws Exception {
    int ruleId = createRule("GT_NET_UPDATE_SERVERLIST_SEL_RR_C", "GT_NET_UPDATE_SERVERLIST_REJECTED_S", 23, 0);
    try {
      assertOk(GTNetPeerTestSupport.submit(GTNetPeerTestSupport.PEER_A, jwtA, remoteB,
          "GT_NET_UPDATE_SERVERLIST_SEL_RR_C", Map.of(), "GTNet peer rejected server list").statusCode(),
          "server-list rejection");
    } finally {
      deleteRule(ruleId);
    }
    ruleId = createRule("GT_NET_UPDATE_SERVERLIST_SEL_RR_C", "GT_NET_UPDATE_SERVERLIST_ACCEPT_S", 24, 0);
    try {
      assertOk(GTNetPeerTestSupport.submit(GTNetPeerTestSupport.PEER_A, jwtA, remoteB,
          "GT_NET_UPDATE_SERVERLIST_SEL_RR_C", Map.of(), "GTNet peer server list").statusCode(), "server-list request");
    } finally {
      deleteRule(ruleId);
    }
    assertOk(GTNetPeerTestSupport.submit(GTNetPeerTestSupport.PEER_A, jwtA, remoteB,
        "GT_NET_UPDATE_SERVERLIST_REVOKE_SEL_C", Map.of(), "GTNet peer server list revoke").statusCode(),
        "server-list revoke");
  }

  @Test
  @Order(5)
  void announcementsAndCancellationArePersistedOnBothSides() throws Exception {
    // Only a change of the published settings enqueues the broadcast, so the test makes one itself.
    int raisedLimit = raiseOwnDailyRequestLimitOfA();
    runNewestWaitingTask(GTNetPeerTestSupport.PEER_A, jwtA, "GTNET_SETTINGS_BROADCAST");
    assertThat(entry(GTNetPeerTestSupport.PEER_B, jwtB, remoteA).path("dailyRequestLimit").asInt())
        .as("peer B synchronised the broadcast limit").isEqualTo(raisedLimit);
    assertOk(GTNetPeerTestSupport.submit(GTNetPeerTestSupport.PEER_A, jwtA, null, "GT_NET_OFFLINE_ALL_C", Map.of(),
        "GTNet peer offline announcement").statusCode(), "offline announcement");
    String from = LocalDateTime.now().plusMinutes(5).truncatedTo(ChronoUnit.SECONDS).toString();
    String to = LocalDateTime.now().plusMinutes(10).truncatedTo(ChronoUnit.SECONDS).toString();
    var maintenance = GTNetPeerTestSupport.submit(GTNetPeerTestSupport.PEER_A, jwtA, null, "GT_NET_MAINTENANCE_ALL_C",
        Map.of("fromDateTime", from, "toDateTime", to), "GTNet peer maintenance");
    assertOk(maintenance.statusCode(), maintenance.body());

    int originalId = newestAttemptOwningMessageId("GT_NET_MAINTENANCE_ALL_C");
    runNewestWaitingTask(GTNetPeerTestSupport.PEER_A, jwtA, "GTNET_FUTURE_MESSAGE_DELIVERY");
    var cancelRequest = GTNetPeerTestSupport.JSON.createObjectNode();
    cancelRequest.put("messageCode", "GT_NET_MAINTENANCE_CANCEL_ALL_C");
    cancelRequest.put("idOriginalMessage", originalId);
    cancelRequest.put("message", "GTNet peer maintenance cancellation");
    cancelRequest.putObject("gtNetMessageParamMap");
    var cancel = GTNetPeerTestSupport.postApi(GTNetPeerTestSupport.PEER_A, "/api/gtnet/submitmsg", jwtA,
        cancelRequest.toString());
    assertOk(cancel.statusCode(), cancel.body());
    runNewestWaitingTask(GTNetPeerTestSupport.PEER_A, jwtA, "GTNET_FUTURE_MESSAGE_DELIVERY");

    var discontinued = GTNetPeerTestSupport.submit(GTNetPeerTestSupport.PEER_A, jwtA, null,
        "GT_NET_OPERATION_DISCONTINUED_ALL_C", Map.of("closeStartDate", LocalDate.now().plusDays(2).toString()),
        "GTNet peer discontinuation");
    assertOk(discontinued.statusCode(), discontinued.body());
    int discontinuedId = newestAttemptOwningMessageId("GT_NET_OPERATION_DISCONTINUED_ALL_C");
    runNewestWaitingTask(GTNetPeerTestSupport.PEER_A, jwtA, "GTNET_FUTURE_MESSAGE_DELIVERY");
    var discontinueCancel = GTNetPeerTestSupport.JSON.createObjectNode();
    discontinueCancel.put("messageCode", "GT_NET_OPERATION_DISCONTINUED_CANCEL_ALL_C");
    discontinueCancel.put("idOriginalMessage", discontinuedId);
    discontinueCancel.put("message", "GTNet peer discontinuation cancellation");
    discontinueCancel.putObject("gtNetMessageParamMap");
    var cancelled = GTNetPeerTestSupport.postApi(GTNetPeerTestSupport.PEER_A, "/api/gtnet/submitmsg", jwtA,
        discontinueCancel.toString());
    assertOk(cancelled.statusCode(), cancelled.body());
    runNewestWaitingTask(GTNetPeerTestSupport.PEER_A, jwtA, "GTNET_FUTURE_MESSAGE_DELIVERY");
  }

  @Test
  @Order(6)
  void singleAndMultiTargetAdminMessagesReachTheRemotePeer() throws Exception {
    assertOk(GTNetPeerTestSupport.submit(GTNetPeerTestSupport.PEER_A, jwtA, remoteB, "GT_NET_ADMIN_MESSAGE_SEL_C",
        Map.of(), "GTNet peer single admin message").statusCode(), "single admin");

    String multi = "{\"idGTNetTargetDomains\":[" + remoteB
        + "],\"message\":\"GTNet peer queued admin message\",\"visibility\":\"ADMIN_ONLY\","
        + "\"gtNetMessageParamMap\":{}}";
    var queued = GTNetPeerTestSupport.postApi(GTNetPeerTestSupport.PEER_A, "/api/gtnet/submitmsgmulti", jwtA, multi);
    assertOk(queued.statusCode(), queued.body());
    // A multi-target request with a single valid target is sent synchronously. The queued delivery through
    // GTNET_ADMIN_MESSAGE_DELIVERY needs at least two peers with a completed handshake, which this topology lacks.
    assertThat(messages(GTNetPeerTestSupport.PEER_B, jwtB, remoteA).toString())
        .contains("GTNet peer queued admin message");
  }

  @Test
  @Order(7)
  void dataExchangeCanBeRevokedAfterProtocolTraffic() throws Exception {
    // A revoke naming no kind would end nothing on either side, so it is refused before it leaves peer A
    var empty = GTNetPeerTestSupport.submit(GTNetPeerTestSupport.PEER_A, jwtA, remoteB, "GT_NET_DATA_REVOKE_SEL_C",
        Map.of(), "GTNet peer empty revoke");
    assertThat(empty.statusCode()).as(empty.body()).isBetween(400, 499);

    assertThat(integrationStreamGrantOfB().path("exchange").asBoolean()).as("grant of the accepted data request")
        .isTrue();
    var revoke = GTNetPeerTestSupport.submit(GTNetPeerTestSupport.PEER_A, jwtA, remoteB, "GT_NET_DATA_REVOKE_SEL_C",
        Map.of("entityKinds", "INTEGRATION_STREAM"), "GTNet peer revoke");
    assertOk(revoke.statusCode(), revoke.body());
    assertThat(integrationStreamGrantOfB().path("exchange").asBoolean()).as("exchange after the revoke").isFalse();
  }

  /** The grant peer A holds on its entry of peer B for INTEGRATION_STREAM, created by the accepted data request. */
  private static JsonNode integrationStreamGrantOfB() throws Exception {
    JsonNode state = GTNetPeerTestSupport.readGTNet(GTNetPeerTestSupport.PEER_A, jwtA);
    int kind = -1;
    for (JsonNode type : state.path("exchangeKindTypes")) {
      if ("INTEGRATION_STREAM".equals(type.path("name").asString())) {
        kind = type.path("value").asInt();
      }
    }
    for (JsonNode entity : entry(GTNetPeerTestSupport.PEER_A, jwtA, remoteB).path("gtNetEntities")) {
      if (entity.path("entityKind").asInt() == kind) {
        return entity.path("gtNetConfigEntity");
      }
    }
    throw new IllegalStateException("Peer A holds no INTEGRATION_STREAM entity for peer B");
  }

  @Test
  @Order(8)
  void invalidM2mAuthenticationNeverProducesAProtocolEnvelope() throws Exception {
    String envelope = """
        {"sourceDomain":"%s","idSourceGtNetMessage":999,"timestamp":"2026-01-01T00:00:00",
         "messageCode":0,"serverBusy":false,"visibility":0}
        """.formatted(requiredEnv("GTNET_PEER_A_OWN_URL"));
    var response = GTNetPeerTestSupport.postJson(GTNetPeerTestSupport.PEER_B, "/m2m/gtnet", "wrong-token", envelope);
    assertThat(response.statusCode()).isNotEqualTo(200);
    assertThat(response.body()).doesNotContain("replyToSourceId");

    var unknown = GTNetPeerTestSupport.postApi(GTNetPeerTestSupport.PEER_A, "/api/gtnet/submitmsg", jwtA,
        "{\"idGTNetTargetDomain\":" + remoteB
            + ",\"messageCode\":\"GT_NET_TEST_UNKNOWN\",\"gtNetMessageParamMap\":{}}");
    assertThat(unknown.statusCode() < 200 || unknown.statusCode() > 299).isTrue();
  }

  @Test
  @Order(9)
  void deterministicTaskTriggerRequiresAdministratorRole() throws Exception {
    String limited = GTNetPeerTestSupport.login(GTNetPeerTestSupport.PEER_A, "limited@test.local");
    var response = GTNetPeerTestSupport.postApi(GTNetPeerTestSupport.PEER_A, "/api/integration-gtnet-test/tasks/1/run",
        limited, "null");
    assertThat(response.statusCode()).isEqualTo(403);
  }

  /**
   * Raises the daily request limit peer A publishes about itself by one, which saveOnlyAttributes answers by enqueuing
   * GTNET_SETTINGS_BROADCAST. One more request per day changes nothing for the later classes of the suite.
   *
   * @return the limit peer A publishes now
   */
  private static int raiseOwnDailyRequestLimitOfA() throws Exception {
    ObjectNode ownEntry = (ObjectNode) entry(GTNetPeerTestSupport.PEER_A, jwtA, ownA);
    int raised = ownEntry.path("dailyRequestLimit").asInt(1000) + 1;
    ownEntry.put("dailyRequestLimit", raised);
    var response = GTNetPeerTestSupport.putApi(GTNetPeerTestSupport.PEER_A, "/api/gtnet", jwtA, ownEntry.toString());
    assertOk(response.statusCode(), response.body());
    return raised;
  }

  private static JsonNode entry(URI peer, String jwt, int idGtNet) throws Exception {
    for (JsonNode entry : GTNetPeerTestSupport.readGTNet(peer, jwt).path("gtNetList")) {
      if (entry.path("idGtNet").asInt() == idGtNet) {
        return entry;
      }
    }
    throw new IllegalStateException("Entry " + idGtNet + " not found at " + peer);
  }

  private static JsonNode messages(URI peer, String jwt, int remoteId) throws Exception {
    var response = GTNetPeerTestSupport.getApi(peer, "/api/gtnet/messages/" + remoteId, jwt);
    assertOk(response.statusCode(), response.body());
    return GTNetPeerTestSupport.JSON.readTree(response.body());
  }

  /**
   * Selects the newest announcement of the given code under the own entry that owns delivery attempts. A cancellation
   * must reference that message: a visibility copy without attempts is never correlated by the peers (issue #238).
   *
   * @param messageCode the name of the future-oriented announcement code
   * @return the id of the attempt-owning message
   */
  private static int newestAttemptOwningMessageId(String messageCode) throws Exception {
    int newest = 0;
    for (JsonNode message : messages(GTNetPeerTestSupport.PEER_A, jwtA, ownA)) {
      int candidate = message.path("idGtNetMessage").asInt();
      if (messageCode.equals(message.path("messageCode").asString()) && !attempts(candidate).isEmpty()) {
        newest = Math.max(newest, candidate);
      }
    }
    assertThat(newest).as("attempt-owning %s", messageCode).isPositive();
    return newest;
  }

  private static JsonNode attempts(int idGtNetMessage) throws Exception {
    var response = GTNetPeerTestSupport.getApi(GTNetPeerTestSupport.PEER_A,
        "/api/integration-gtnet-test/messages/" + idGtNetMessage + "/attempts", jwtA);
    assertOk(response.statusCode(), response.body());
    return GTNetPeerTestSupport.JSON.readTree(response.body());
  }

  private static void runNewestWaitingTask(URI peer, String jwt, String taskName) throws Exception {
    var response = GTNetPeerTestSupport.getApi(peer, "/api/taskdatachange", jwt);
    assertOk(response.statusCode(), response.body());
    int id = 0;
    for (JsonNode task : GTNetPeerTestSupport.JSON.readTree(response.body())) {
      if (taskName.equals(task.path("idTask").asString())) {
        id = Math.max(id, task.path("idTaskDataChange").asInt());
      }
    }
    assertThat(id).as("waiting task %s", taskName).isPositive();
    var run = GTNetPeerTestSupport.postApi(peer, "/api/integration-gtnet-test/tasks/" + id + "/run", jwt, "null");
    assertOk(run.statusCode(), run.body());
    assertThat(run.body()).contains("PROG_PROCESSED");
  }

  private static int createRule(String requestCode, String responseCode, int priority, int waitDays) throws Exception {
    deleteRuleAtPriority(priority);
    String body = "{\"requestMsgCode\":\"" + requestCode + "\",\"responseMsgCode\":\"" + responseCode
        + "\",\"priority\":" + priority + ",\"waitDaysApply\":" + waitDays + "}";
    var response = GTNetPeerTestSupport.postApi(GTNetPeerTestSupport.PEER_B, "/api/gtnetmessageanswer", jwtB, body);
    assertOk(response.statusCode(), response.body());
    return GTNetPeerTestSupport.JSON.readTree(response.body()).path("idGtNetMessageAnswer").asInt();
  }

  private static void deleteRuleAtPriority(int priority) throws Exception {
    var response = GTNetPeerTestSupport.getApi(GTNetPeerTestSupport.PEER_B, "/api/gtnetmessageanswer", jwtB);
    assertOk(response.statusCode(), response.body());
    for (JsonNode rule : GTNetPeerTestSupport.JSON.readTree(response.body())) {
      if (rule.path("priority").asInt() == priority) {
        deleteRule(rule.path("idGtNetMessageAnswer").asInt());
      }
    }
  }

  private static void deleteRule(int id) throws Exception {
    var response = GTNetPeerTestSupport.deleteApi(GTNetPeerTestSupport.PEER_B, "/api/gtnetmessageanswer/" + id, jwtB);
    assertOk(response.statusCode(), response.body());
  }

  private static void assertOk(int status, String body) {
    assertThat(status).as(body).isBetween(200, 299);
  }

  private static String requiredEnv(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " is required; run this suite through e2eTest --gtnet");
    }
    return value;
  }
}
