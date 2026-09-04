package grafiosch.gtnet.peer;

import static org.assertj.core.api.Assertions.assertThat;

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

/**
 * A future-dated announcement is not sent when it is submitted. It is stored as PENDING with one GTNetMessageAttempt
 * per target, and GNetFutureMessageDeliveryTask turns those attempts into deliveries later.
 *
 * The peers run with the background worker disabled, so the task is invoked explicitly instead of being picked up by
 * the 15 second poll; waiting on that poll is never an assertion strategy.
 *
 * The e2e-only control endpoint can mark the local view of peer B out of service without stopping its JVM. That stages
 * the terminal skip deterministically and is reset before the test ends, so the topology remains reusable.
 */
@TestMethodOrder(OrderAnnotation.class)
class GTNetPeerFutureDeliveryTest {

  private static final String DEAD_PEER_DOMAIN = "http://unreachable-peer.example:8098";

  private static String jwtA;
  private static int ownA;
  private static int remoteB;
  private static int announcementId;

  @BeforeAll
  static void connect() throws Exception {
    jwtA = GTNetPeerTestSupport.loginAdmin(GTNetPeerTestSupport.PEER_A);
    JsonNode stateA = GTNetPeerTestSupport.readGTNet(GTNetPeerTestSupport.PEER_A, jwtA);
    ownA = stateA.path("gtNetMyEntryId").asInt();
    remoteB = GTNetPeerTestSupport.remoteId(stateA, requiredEnv("GTNET_PEER_B_OWN_URL"));
  }

  @Test
  @Order(1)
  void aFutureAnnouncementIsStoredPendingWithAnUnsentAttemptPerConfiguredTarget() throws Exception {
    String from = LocalDateTime.now().plusDays(2).truncatedTo(ChronoUnit.SECONDS).toString();
    String to = LocalDateTime.now().plusDays(3).truncatedTo(ChronoUnit.SECONDS).toString();
    var response = GTNetPeerTestSupport.submit(GTNetPeerTestSupport.PEER_A, jwtA, null, "GT_NET_MAINTENANCE_ALL_C",
        Map.of("fromDateTime", from, "toDateTime", to), "GTNet peer future maintenance");
    assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);

    announcementId = newestSentAnnouncementId();
    assertThat(deliveryStatusOf(announcementId)).isEqualTo("PENDING");

    JsonNode attempts = attempts(announcementId);
    assertThat(attempts).as("one attempt per peer with configured exchange").isNotEmpty();
    for (JsonNode attempt : attempts) {
      assertThat(attempt.path("hasSend").asBoolean()).isFalse();
      assertThat(attempt.path("sendTimestamp").isNull() || attempt.path("sendTimestamp").isMissingNode()).isTrue();
      assertThat(attempt.path("attemptStatus").asString()).isEqualTo("QUEUED");
      assertThat(attempt.path("tryCount").asInt()).isZero();
    }
    assertThat(targetIds(attempts)).contains(remoteB);
  }

  @Test
  @Order(2)
  void aStrangerWithoutConfiguredExchangeNeverGetsAnAttempt() throws Exception {
    createDeadPeer();
    int deadPeerId = idOf(DEAD_PEER_DOMAIN);

    assertThat(targetIds(attempts(announcementId))).doesNotContain(deadPeerId);

    deleteDeadPeer();
  }

  @Test
  @Order(3)
  void runningTheTaskSendsTheAttemptsAndMarksTheMessageDelivered() throws Exception {
    runNewestWaitingTask("GTNET_FUTURE_MESSAGE_DELIVERY");

    JsonNode attempts = attempts(announcementId);
    assertThat(attempts).isNotEmpty();
    for (JsonNode attempt : attempts) {
      assertThat(attempt.path("hasSend").asBoolean()).as("attempt to peer %s", attempt.path("idGtNet").asInt())
          .isTrue();
      assertThat(attempt.path("attemptStatus").asString()).isEqualTo("DELIVERED");
      assertThat(attempt.path("tryCount").asInt()).isEqualTo(1);
      assertThat(attempt.path("sendTimestamp").asString()).isNotBlank();
    }
    assertThat(deliveryStatusOf(announcementId)).isEqualTo("DELIVERED");

    var productionView = GTNetPeerTestSupport.getApi(GTNetPeerTestSupport.PEER_A, "/api/gtnet/messageattempts/" + ownA,
        jwtA);
    assertThat(productionView.statusCode()).as(productionView.body()).isBetween(200, 299);
    assertThat(productionView.body()).contains("\"attemptStatus\":\"DELIVERED\"");
  }

  @Test
  @Order(4)
  void aRetiredOnlyTargetMakesItsAttemptAndMessageFailed() throws Exception {
    submitCancellation(announcementId, "GTNet peer future maintenance cancellation");
    runNewestWaitingTask("GTNET_FUTURE_MESSAGE_DELIVERY");

    String from = LocalDateTime.now().plusDays(4).truncatedTo(ChronoUnit.SECONDS).toString();
    String to = LocalDateTime.now().plusDays(5).truncatedTo(ChronoUnit.SECONDS).toString();
    var response = GTNetPeerTestSupport.submit(GTNetPeerTestSupport.PEER_A, jwtA, null, "GT_NET_MAINTENANCE_ALL_C",
        Map.of("fromDateTime", from, "toDateTime", to), "GTNet peer retired-target maintenance");
    assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
    int retiredAnnouncementId = newestAttemptOwningAnnouncementId();

    setRemoteBOnlineStatus(3);
    try {
      runNewestWaitingTask("GTNET_FUTURE_MESSAGE_DELIVERY");
      JsonNode retiredAttempts = attempts(retiredAnnouncementId);
      assertThat(retiredAttempts).hasSize(1);
      assertThat(retiredAttempts.get(0).path("attemptStatus").asString()).isEqualTo("PEER_OUT_OF_SERVICE");
      assertThat(retiredAttempts.get(0).path("tryCount").asInt()).isZero();
      assertThat(deliveryStatusOf(retiredAnnouncementId)).isEqualTo("FAILED");
    } finally {
      setRemoteBOnlineStatus(1);
    }

    submitCancellation(retiredAnnouncementId, "GTNet peer retired-target maintenance cancellation");
    runNewestWaitingTask("GTNET_FUTURE_MESSAGE_DELIVERY");
  }

  private static java.util.List<Integer> targetIds(JsonNode attempts) {
    java.util.List<Integer> ids = new java.util.ArrayList<>();
    for (JsonNode attempt : attempts) {
      ids.add(attempt.path("idGtNet").asInt());
    }
    return ids;
  }

  private static JsonNode attempts(int idGtNetMessage) throws Exception {
    var response = GTNetPeerTestSupport.getApi(GTNetPeerTestSupport.PEER_A,
        "/api/integration-gtnet-test/messages/" + idGtNetMessage + "/attempts", jwtA);
    assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
    return GTNetPeerTestSupport.JSON.readTree(response.body());
  }

  /** Selects the source message that owns attempts, never a legacy visibility copy with a higher identity. */
  private static int newestSentAnnouncementId() throws Exception {
    return newestAttemptOwningAnnouncementId();
  }

  private static int newestAttemptOwningAnnouncementId() throws Exception {
    int newest = 0;
    for (JsonNode message : messages(ownA)) {
      int candidate = message.path("idGtNetMessage").asInt();
      if ("GT_NET_MAINTENANCE_ALL_C".equals(message.path("messageCode").asString()) && !attempts(candidate).isEmpty()) {
        newest = Math.max(newest, candidate);
      }
    }
    assertThat(newest).as("the maintenance announcement was stored").isPositive();
    return newest;
  }

  private static String deliveryStatusOf(int idGtNetMessage) throws Exception {
    for (JsonNode message : messages(ownA)) {
      if (message.path("idGtNetMessage").asInt() == idGtNetMessage) {
        return message.path("deliveryStatus").asString();
      }
    }
    throw new IllegalStateException("Message " + idGtNetMessage + " not found");
  }

  private static JsonNode messages(int idGtNet) throws Exception {
    var response = GTNetPeerTestSupport.getApi(GTNetPeerTestSupport.PEER_A, "/api/gtnet/messages/" + idGtNet, jwtA);
    assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
    return GTNetPeerTestSupport.JSON.readTree(response.body());
  }

  private static void createDeadPeer() throws Exception {
    if (idOf(DEAD_PEER_DOMAIN) > 0) {
      return;
    }
    var response = GTNetPeerTestSupport.postApi(GTNetPeerTestSupport.PEER_A, "/api/gtnet", jwtA,
        "{\"domainRemoteName\":\"" + DEAD_PEER_DOMAIN + "\"}");
    assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
  }

  private static void deleteDeadPeer() throws Exception {
    int id = idOf(DEAD_PEER_DOMAIN);
    if (id > 0) {
      GTNetPeerTestSupport.deleteApi(GTNetPeerTestSupport.PEER_A, "/api/gtnet/" + id, jwtA);
    }
  }

  private static int idOf(String domain) throws Exception {
    for (JsonNode entry : GTNetPeerTestSupport.readGTNet(GTNetPeerTestSupport.PEER_A, jwtA).path("gtNetList")) {
      if (domain.equals(entry.path("domainRemoteName").asString())) {
        return entry.path("idGtNet").asInt();
      }
    }
    return 0;
  }

  private static void runNewestWaitingTask(String taskName) throws Exception {
    var response = GTNetPeerTestSupport.getApi(GTNetPeerTestSupport.PEER_A, "/api/taskdatachange", jwtA);
    assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
    int id = 0;
    for (JsonNode task : GTNetPeerTestSupport.JSON.readTree(response.body())) {
      if (taskName.equals(task.path("idTask").asString())) {
        id = Math.max(id, task.path("idTaskDataChange").asInt());
      }
    }
    assertThat(id).as("waiting task %s", taskName).isPositive();
    var run = GTNetPeerTestSupport.postApi(GTNetPeerTestSupport.PEER_A,
        "/api/integration-gtnet-test/tasks/" + id + "/run", jwtA, "null");
    assertThat(run.statusCode()).as(run.body()).isBetween(200, 299);
    assertThat(run.body()).contains("PROG_PROCESSED");
  }

  private static void submitCancellation(int originalId, String message) throws Exception {
    var request = GTNetPeerTestSupport.JSON.createObjectNode();
    request.put("messageCode", "GT_NET_MAINTENANCE_CANCEL_ALL_C");
    request.put("idOriginalMessage", originalId);
    request.put("message", message);
    request.putObject("gtNetMessageParamMap");
    var response = GTNetPeerTestSupport.postApi(GTNetPeerTestSupport.PEER_A, "/api/gtnet/submitmsg", jwtA,
        request.toString());
    assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
  }

  private static void setRemoteBOnlineStatus(int status) throws Exception {
    var response = GTNetPeerTestSupport.postApi(GTNetPeerTestSupport.PEER_A,
        "/api/integration-gtnet-test/peers/" + remoteB + "/online-status/" + status, jwtA, "null");
    assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
  }

  private static String requiredEnv(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " is required; run this suite through e2eTest --gtnet");
    }
    return value;
  }
}
