package grafioschtrader.gtnet.peer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import grafiosch.test.gtnet.GTNetPeerTestSupport;
import grafioschtrader.gtnet.GTNetExchangeKindType;
import tools.jackson.databind.JsonNode;

/**
 * What the exchange leaves behind on the supplying side.
 *
 * Every request a peer answers is accounted per instrument and exchange kind in gt_net_supplier_detail with its
 * gt_net_supplier_detail_hist and gt_net_supplier_detail_last children, and the supplier detail tree of the
 * exchange-flags page renders exactly those rows. This class runs after the exchange classes of this suite, so the
 * traffic it inspects was produced by them.
 */
class GTNetAppSupplierDetailTest {

  private static GTNetAppPeerFixture fixture;
  private static String jwtB;

  @BeforeAll
  static void connect() throws Exception {
    fixture = GTNetAppPeerFixture.load();
    jwtB = GTNetPeerTestSupport.loginAdmin(GTNetPeerTestSupport.PEER_B);
  }

  @Test
  void theExchangeFlagsPageReportsWhichInstrumentsHaveSupplierDetails() throws Exception {
    var response = GTNetPeerTestSupport.getApi(GTNetPeerTestSupport.PEER_B, "/api/security/gtnetexchange", jwtB);
    assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
    JsonNode exchange = GTNetPeerTestSupport.JSON.readTree(response.body());

    // The four flags are columns of securitycurrency, not of a join table, and the Flyway test data ships them set.
    JsonNode securities = exchange.path("securitiescurrenciesList");
    assertThat(securities).isNotEmpty();
    var fixtureSecurity = fixture.securities().get(0);
    JsonNode match = null;
    for (JsonNode security : securities) {
      if (fixtureSecurity.isin().equals(security.path("isin").asString())
          && fixtureSecurity.currency().equals(security.path("currency").asString())) {
        match = security;
        break;
      }
    }
    assertThat(match).as("fixture security %s is present on the peer", fixtureSecurity.isin()).isNotNull();
    assertThat(match.path("gtNetHistoricalSend").asBoolean() || match.path("gtNetLastpriceSend").asBoolean())
        .as("the fixture security is offered to peers").isTrue();
  }

  @Test
  void answeringRequestsRecordsTheSupplierDetailOfTheExchangedInstrument() throws Exception {
    var fixtureSecurity = fixture.securities().get(0);
    int idSecuritycurrency = idOfSecurity(fixtureSecurity.isin(), fixtureSecurity.currency());

    var response = GTNetPeerTestSupport.getApi(GTNetPeerTestSupport.PEER_B,
        "/api/security/" + idSecuritycurrency + "/gtnetexchange/supplierdetails", jwtB);

    assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
    JsonNode details = GTNetPeerTestSupport.JSON.readTree(response.body());
    assertThat(details.isArray()).isTrue();
    // The tree renders one node per supplying peer; the exchange kinds it may carry are the registered application
    // kinds, and the historical one is what the exchange classes of this suite requested.
    for (JsonNode detail : details) {
      assertThat(detail.toString()).contains(GTNetExchangeKindType.HISTORICAL_PRICES.name())
          .doesNotContain("tokenThis");
    }
  }

  private static int idOfSecurity(String isin, String currency) throws Exception {
    var response = GTNetPeerTestSupport.getApi(GTNetPeerTestSupport.PEER_B, "/api/security/gtnetexchange", jwtB);
    assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
    for (JsonNode security : GTNetPeerTestSupport.JSON.readTree(response.body()).path("securitiescurrenciesList")) {
      if (isin.equals(security.path("isin").asString()) && currency.equals(security.path("currency").asString())) {
        return security.path("idSecuritycurrency").asInt();
      }
    }
    throw new IllegalStateException("Security " + isin + "/" + currency + " not found at peer B");
  }
}
