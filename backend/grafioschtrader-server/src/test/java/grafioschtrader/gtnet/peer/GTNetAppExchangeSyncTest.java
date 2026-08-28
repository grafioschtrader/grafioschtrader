package grafioschtrader.gtnet.peer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import grafiosch.test.gtnet.GTNetPeerTestSupport;
import grafiosch.test.gtnet.SyntheticPeer;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.model.msg.ExchangeSyncMsg;
import tools.jackson.databind.JsonNode;

/**
 * Exchange configuration sync, from both ends.
 *
 * The answering side is driven by a synthetic peer, which is the only way to observe the response envelope of a
 * programmatic code. The requesting side is driven through GTNetExchangeSyncTask, invoked synchronously through the
 * e2e-only trigger rather than waiting for the background worker, whose 15 second poll is disabled on both peers.
 */
@TestMethodOrder(OrderAnnotation.class)
class GTNetAppExchangeSyncTest {

  private static final String SYNTHETIC_DOMAIN = "http://exchangesync-probe.example:8099";

  private static GTNetAppPeerFixture fixture;
  private static SyntheticPeer synthetic;
  private static String jwtA;
  private static String jwtB;

  @BeforeAll
  static void connect() throws Exception {
    fixture = GTNetAppPeerFixture.load();
    jwtA = GTNetPeerTestSupport.loginAdmin(GTNetPeerTestSupport.PEER_A);
    jwtB = GTNetPeerTestSupport.loginAdmin(GTNetPeerTestSupport.PEER_B);
    synthetic = SyntheticPeer.connect(GTNetPeerTestSupport.PEER_B, SYNTHETIC_DOMAIN);
    // A completed handshake no longer entitles a peer to price data: serving it needs an accepted,
    // unrevoked grant for this peer and this kind. The synthetic peer asks for one the way a real peer does.
    synthetic.grantDataExchange(jwtB, "LAST_PRICE", "HISTORICAL_PRICES");
  }

  @AfterAll
  static void removeSyntheticPeer() throws Exception {
    if (synthetic != null) {
      synthetic.disconnect(jwtB);
    }
  }

  @Test
  @Order(1)
  void aSyncRequestIsAnsweredWithTheRemoteChanges() throws Exception {
    var security = fixture.securities().get(0);
    ExchangeSyncMsg.ExchangeSyncItem item = new ExchangeSyncMsg.ExchangeSyncItem();
    item.isin = security.isin();
    item.currency = security.currency();
    item.lastpriceSend = true;
    item.historicalSend = true;

    JsonNode reply = synthetic.send(GTNetMessageCodeType.GT_NET_EXCHANGE_SYNC_SEL_RR_C.getValue(),
        ExchangeSyncMsg.forRequest(LocalDateTime.now().minusYears(1), List.of(item)));

    assertThat(reply.path("messageCode").asInt())
        .isEqualTo(GTNetMessageCodeType.GT_NET_EXCHANGE_SYNC_RESPONSE_S.getValue());
    assertThat(reply.path("payload").isMissingNode()).isFalse();
  }

  @Test
  @Order(2)
  void theRequesterSideRunsTheSyncTaskToCompletion() throws Exception {
    // Reaching PROG_PROCESSED proves nothing on its own: syncWithPeer returns false when the peer refuses and the
    // task completes all the same. What only a peer that actually answered can produce is the supplier detail this
    // side writes from the response, which is what moves supplierLastUpdate forward.
    String before = supplierLastUpdateForPeerB();

    int idTaskDataChange = enqueueExchangeSync();
    var run = GTNetPeerTestSupport.postApi(GTNetPeerTestSupport.PEER_A,
        "/api/gtnet-peer-test/tasks/" + idTaskDataChange + "/run", jwtA, "null");

    assertThat(run.statusCode()).as(run.body()).isBetween(200, 299);
    assertThat(run.body()).contains("PROG_PROCESSED");

    String after = supplierLastUpdateForPeerB();
    assertThat(after).as("peer B answered the sync request").isNotNull().isNotEqualTo(before);
  }

  /**
   * The moment peer A last wrote supplier details from peer B's answer, as it stands right now.
   *
   * @return the serialized timestamp, null while no answer has ever been processed
   */
  private static String supplierLastUpdateForPeerB() throws Exception {
    JsonNode state = GTNetPeerTestSupport.readGTNet(GTNetPeerTestSupport.PEER_A, jwtA);
    int ownId = state.path("gtNetMyEntryId").asInt();
    for (JsonNode entry : state.path("gtNetList")) {
      if (entry.path("idGtNet").asInt() != ownId) {
        JsonNode lastUpdate = entry.path("gtNetConfig").path("supplierLastUpdate");
        return lastUpdate.isMissingNode() || lastUpdate.isNull() ? null : lastUpdate.toString();
      }
    }
    throw new IllegalStateException("Peer A knows no remote entry");
  }

  /**
   * Queues a GTNET_EXCHANGE_SYNC row the way the exchange-flags page does when it is left after a save, and returns its
   * id.
   *
   * @return the id of the queued task row
   */
  private static int enqueueExchangeSync() throws Exception {
    var trigger = GTNetPeerTestSupport.postApi(GTNetPeerTestSupport.PEER_A, "/api/security/gtnetexchange/triggersync",
        jwtA, "null");
    assertThat(trigger.statusCode()).as(trigger.body()).isBetween(200, 299);

    var response = GTNetPeerTestSupport.getApi(GTNetPeerTestSupport.PEER_A, "/api/taskdatachange", jwtA);
    assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
    int id = 0;
    for (JsonNode task : GTNetPeerTestSupport.JSON.readTree(response.body())) {
      if ("GTNET_EXCHANGE_SYNC".equals(task.path("idTask").asString())) {
        id = Math.max(id, task.path("idTaskDataChange").asInt());
      }
    }
    assertThat(id).as("a GTNET_EXCHANGE_SYNC row is waiting").isPositive();
    return id;
  }
}
