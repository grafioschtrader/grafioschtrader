package grafiosch.gtnet.peer;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

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
 * A ping carries the answering peer's own metadata, and {@code updateRemoteGTNetFromEnvelope} applies it to the
 * requester's copy of that peer. The interesting part is the busy flag: while the remote reports itself busy every
 * entity of the stored remote is forced to SS_CLOSED, and the accepting entities go back to SS_OPEN when it clears.
 */
@TestMethodOrder(OrderAnnotation.class)
class GTNetPeerPingMetadataTest {

  private static final String PEER_B_TIME_ZONE = "America/New_York";
  private static final int PEER_B_DAILY_REQUEST_LIMIT = 1234;

  private static String jwtA;
  private static String jwtB;
  private static int remoteB;
  private static ObjectNode originalOwnB;

  @BeforeAll
  static void connect() throws Exception {
    jwtA = GTNetPeerTestSupport.loginAdmin(GTNetPeerTestSupport.PEER_A);
    jwtB = GTNetPeerTestSupport.loginAdmin(GTNetPeerTestSupport.PEER_B);
    remoteB = GTNetPeerTestSupport.remoteId(GTNetPeerTestSupport.readGTNet(GTNetPeerTestSupport.PEER_A, jwtA),
        requiredEnv("GTNET_PEER_B_OWN_URL"));
    originalOwnB = ownEntry(GTNetPeerTestSupport.PEER_B, jwtB).deepCopy();
  }

  @AfterAll
  static void restorePeerB() throws Exception {
    if (originalOwnB != null) {
      saveOwnEntry(GTNetPeerTestSupport.PEER_B, jwtB, originalOwnB);
      ping();
    }
  }

  @Test
  @Order(1)
  void pingCopiesTheAnsweringPeersMetadataOntoTheStoredRemote() throws Exception {
    ObjectNode ownB = ownEntry(GTNetPeerTestSupport.PEER_B, jwtB);
    ownB.put("timeZone", PEER_B_TIME_ZONE);
    ownB.put("spreadCapability", true);
    ownB.put("dailyRequestLimit", PEER_B_DAILY_REQUEST_LIMIT);
    saveOwnEntry(GTNetPeerTestSupport.PEER_B, jwtB, ownB);

    ping();

    JsonNode storedRemote = remoteEntry(GTNetPeerTestSupport.PEER_A, jwtA, remoteB);
    assertThat(storedRemote.path("timeZone").asText()).isEqualTo(PEER_B_TIME_ZONE);
    assertThat(storedRemote.path("spreadCapability").asBoolean()).isTrue();
    assertThat(storedRemote.path("dailyRequestLimit").asInt()).isEqualTo(PEER_B_DAILY_REQUEST_LIMIT);
    assertThat(storedRemote.path("serverOnline").asText()).contains("SOS_ONLINE");
  }

  @Test
  @Order(2)
  void aBusyPeerClosesEveryEntityOfTheStoredRemote() throws Exception {
    setServerBusy(true);

    ping();

    JsonNode entities = remoteEntry(GTNetPeerTestSupport.PEER_A, jwtA, remoteB).path("gtNetEntities");
    assertThat(entities).isNotEmpty();
    for (JsonNode entity : entities) {
      assertThat(entity.path("serverState").asText()).as("entity %s", entity.path("entityKind").asText())
          .isEqualTo("SS_CLOSED");
    }
  }

  @Test
  @Order(3)
  void clearingBusyReopensTheAcceptingEntities() throws Exception {
    setServerBusy(false);

    ping();

    JsonNode entities = remoteEntry(GTNetPeerTestSupport.PEER_A, jwtA, remoteB).path("gtNetEntities");
    assertThat(entities).isNotEmpty();
    boolean anyAccepting = false;
    for (JsonNode entity : entities) {
      if (!"AC_CLOSED".equals(entity.path("acceptRequest").asText())) {
        anyAccepting = true;
        assertThat(entity.path("serverState").asText()).as("entity %s", entity.path("entityKind").asText())
            .isEqualTo("SS_OPEN");
      }
    }
    assertThat(anyAccepting).as("peer B has at least one accepting entity kind").isTrue();
  }

  private static void setServerBusy(boolean busy) throws Exception {
    ObjectNode ownB = ownEntry(GTNetPeerTestSupport.PEER_B, jwtB);
    ownB.put("serverBusy", busy);
    saveOwnEntry(GTNetPeerTestSupport.PEER_B, jwtB, ownB);
  }

  private static void ping() throws Exception {
    var response = GTNetPeerTestSupport.postApi(GTNetPeerTestSupport.PEER_A, "/api/gtnet/" + remoteB + "/checkstatus",
        jwtA, "null");
    assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
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
