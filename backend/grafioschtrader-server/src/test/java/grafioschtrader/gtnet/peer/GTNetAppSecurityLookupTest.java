package grafioschtrader.gtnet.peer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
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
import grafioschtrader.gtnet.model.msg.SecurityBatchLookupMsg;
import grafioschtrader.gtnet.model.msg.SecurityLookupMsg;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Security metadata lookup between two real application peers.
 *
 * Two directions are covered. The single lookup is driven from a synthetic peer so the found, not-found and rejected
 * answers can be observed on the wire; the rejected code is the one that used to be unreachable, because the handler
 * answered a refusal as a transport error instead of as a protocol response. The requester side is driven through
 * GTNetSecurityLookupResource on peer A, which is the production client path the security dialog uses.
 */
@TestMethodOrder(OrderAnnotation.class)
class GTNetAppSecurityLookupTest {

  private static final String SYNTHETIC_DOMAIN = "http://lookup-probe.example:8099";

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
  }

  @AfterAll
  static void restorePeerB() throws Exception {
    setMetadataAcceptRequest("AC_OPEN");
    if (synthetic != null) {
      synthetic.disconnect(jwtB);
    }
  }

  @Test
  @Order(1)
  void aKnownInstrumentIsAnsweredWithItsMetadata() throws Exception {
    var security = fixture.securities().get(0);

    JsonNode reply = synthetic.send(GTNetMessageCodeType.GT_NET_SECURITY_LOOKUP_SEL_C.getValue(),
        new SecurityLookupMsg(security.isin(), security.currency(), null));

    assertThat(reply.path("messageCode").asInt())
        .isEqualTo(GTNetMessageCodeType.GT_NET_SECURITY_LOOKUP_RESPONSE_S.getValue());
    assertThat(reply.path("payload").toString()).contains(security.isin());
  }

  @Test
  @Order(2)
  void anUnknownInstrumentIsAnsweredWithNotFound() throws Exception {
    var unknown = fixture.unknownSecurity();

    JsonNode reply = synthetic.send(GTNetMessageCodeType.GT_NET_SECURITY_LOOKUP_SEL_C.getValue(),
        new SecurityLookupMsg(unknown.isin(), unknown.currency(), null));

    assertThat(reply.path("messageCode").asInt())
        .isEqualTo(GTNetMessageCodeType.GT_NET_SECURITY_LOOKUP_NOT_FOUND_S.getValue());
  }

  @Test
  @Order(3)
  void aBatchLookupIsAnsweredForEveryQuery() throws Exception {
    List<SecurityLookupMsg> queries = new ArrayList<>();
    for (var security : fixture.securities()) {
      queries.add(new SecurityLookupMsg(security.isin(), security.currency(), null));
    }

    JsonNode reply = synthetic.send(GTNetMessageCodeType.GT_NET_SECURITY_BATCH_LOOKUP_SEL_C.getValue(),
        new SecurityBatchLookupMsg(queries));

    assertThat(reply.path("messageCode").asInt())
        .isEqualTo(GTNetMessageCodeType.GT_NET_SECURITY_BATCH_LOOKUP_RESPONSE_S.getValue());
    assertThat(reply.path("payload").isMissingNode()).isFalse();
  }

  @Test
  @Order(4)
  void aPeerThatStoppedAcceptingMetadataAnswersRejectedRatherThanNotFound() throws Exception {
    setMetadataAcceptRequest("AC_CLOSED");
    try {
      var security = fixture.securities().get(0);

      JsonNode reply = synthetic.send(GTNetMessageCodeType.GT_NET_SECURITY_LOOKUP_SEL_C.getValue(),
          new SecurityLookupMsg(security.isin(), security.currency(), null));

      assertThat(reply.path("messageCode").asInt())
          .isEqualTo(GTNetMessageCodeType.GT_NET_SECURITY_LOOKUP_REJECTED_S.getValue());
      assertThat(reply.path("message").asString()).contains("not accepting");
    } finally {
      setMetadataAcceptRequest("AC_OPEN");
    }
  }

  @Test
  @Order(5)
  void theRequesterSideFindsTheInstrumentThroughTheProductionLookupEndpoint() throws Exception {
    var haspeers = GTNetPeerTestSupport.getApi(GTNetPeerTestSupport.PEER_A, "/api/gtnetsecuritylookup/haspeers", jwtA);
    assertThat(haspeers.statusCode()).as(haspeers.body()).isBetween(200, 299);

    var security = fixture.securities().get(0);
    String request = "{\"isin\":\"" + security.isin() + "\",\"currency\":\"" + security.currency() + "\"}";
    var response = GTNetPeerTestSupport.postApi(GTNetPeerTestSupport.PEER_A, "/api/gtnetsecuritylookup/lookup", jwtA,
        request);

    assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
    assertThat(response.body()).contains(security.isin());
  }

  /** Switches the security-metadata entity kind of peer B, which is what decides between an answer and a refusal. */
  private static void setMetadataAcceptRequest(String acceptRequest) throws Exception {
    JsonNode state = GTNetPeerTestSupport.readGTNet(GTNetPeerTestSupport.PEER_B, jwtB);
    int ownId = state.path("gtNetMyEntryId").asInt();
    for (JsonNode entry : state.path("gtNetList")) {
      if (entry.path("idGtNet").asInt() == ownId) {
        ObjectNode ownEntry = ((ObjectNode) entry).deepCopy();
        for (JsonNode entity : ownEntry.path("gtNetEntities")) {
          if ("SECURITY_METADATA".equals(entity.path("entityKind").asString())) {
            ((ObjectNode) entity).put("acceptRequest", acceptRequest);
          }
        }
        var response = GTNetPeerTestSupport.putApi(GTNetPeerTestSupport.PEER_B, "/api/gtnet", jwtB,
            ownEntry.toString());
        assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
        return;
      }
    }
    throw new IllegalStateException("Peer B has no own entry");
  }
}
