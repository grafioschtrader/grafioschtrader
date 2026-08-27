package grafiosch.gtnet.peer;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;

import grafiosch.test.gtnet.GTNetPeerTestSupport;

/** Protocol perimeter checks that complement the authenticated two-browser scenarios. */
class GTNetPeerProtocolTest {

  @Test
  void bothPeersExposeTheExpectedE2EIdentity() throws Exception {
    assertPeer(GTNetPeerTestSupport.PEER_A, "grafiosch_t", false);
    assertPeer(GTNetPeerTestSupport.PEER_B, "grafiosch_t1", true);
  }

  @Test
  void m2mRejectsMissingAndInvalidTokensOnBothPeers() throws Exception {
    String pingEnvelope = """
        {"sourceDomain":"http://invalid.example","idSourceGtNetMessage":1,
         "timestamp":"2026-01-01T00:00:00","messageCode":0,"serverBusy":false,"visibility":0}
        """;
    for (URI peer : new URI[] { GTNetPeerTestSupport.PEER_A, GTNetPeerTestSupport.PEER_B }) {
      assertThat(GTNetPeerTestSupport.postJson(peer, "/m2m/gtnet", null, pingEnvelope).statusCode()).isNotEqualTo(200);
      assertThat(GTNetPeerTestSupport.postJson(peer, "/m2m/gtnet", "invalid-token", pingEnvelope).statusCode())
          .isNotEqualTo(200);
    }
  }

  private void assertPeer(URI peer, String database, boolean peerProfile) throws Exception {
    var response = GTNetPeerTestSupport.get(peer, "/api/integration-info");
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"databaseName\":\"" + database + "\"").contains("\"e2e\"");
    if (peerProfile) {
      assertThat(response.body()).contains("\"e2e-peer\"");
    }
  }
}
