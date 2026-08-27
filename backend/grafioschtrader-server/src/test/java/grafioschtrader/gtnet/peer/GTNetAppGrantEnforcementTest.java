package grafioschtrader.gtnet.peer;

import static org.assertj.core.api.Assertions.assertThat;

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
import grafioschtrader.gtnet.m2m.model.InstrumentPriceDTO;
import grafioschtrader.gtnet.model.msg.LastpriceExchangeMsg;
import grafioschtrader.gtnet.model.msg.SecurityLookupMsg;
import tools.jackson.databind.JsonNode;

/**
 * What a peer may ask for once it is connected.
 *
 * <p>
 * Every inbound data handler used to test this instance's own accept flag and nothing else. That flag is global - "do I
 * serve last prices at all" - so possession of a token plus a globally open entity was enough, and a peer that had
 * merely completed a handshake could ask for everything. Serving the syncable kinds now also requires an accepted,
 * unrevoked grant for that peer and that kind.
 * </p>
 *
 * <p>
 * Security metadata deliberately keeps the accept-flag gate. It is not syncable, its codes are an on-demand lookup
 * rather than an exchange, and there is no data request that could grant it.
 * </p>
 */
@TestMethodOrder(OrderAnnotation.class)
class GTNetAppGrantEnforcementTest {

  /** RFC 2606 reserved name; peer B stores it as an ordinary remote entry and never dials it. */
  private static final String SYNTHETIC_DOMAIN = "http://grant-probe.example:8099";
  private static final byte ERROR = 23;

  private static GTNetAppPeerFixture fixture;
  private static SyntheticPeer synthetic;
  private static String jwtB;

  @BeforeAll
  static void connect() throws Exception {
    fixture = GTNetAppPeerFixture.load();
    jwtB = GTNetPeerTestSupport.loginAdmin(GTNetPeerTestSupport.PEER_B);
    // Connected but never granted anything: exactly the state the gate is about.
    synthetic = SyntheticPeer.connect(GTNetPeerTestSupport.PEER_B, SYNTHETIC_DOMAIN);
  }

  @AfterAll
  static void removeSyntheticPeer() throws Exception {
    if (synthetic != null) {
      synthetic.disconnect(jwtB);
    }
  }

  @Test
  @Order(1)
  void refusesLastPricesToAPeerWithoutAnAcceptedExchange() throws Exception {
    JsonNode reply = synthetic.send(GTNetMessageCodeType.GT_NET_LASTPRICE_EXCHANGE_SEL_C.getValue(),
        lastpriceRequest());

    assertRefusedForWantOfAGrant(reply);
  }

  @Test
  @Order(2)
  void refusesHistoricalQuotesToAPeerWithoutAnAcceptedExchange() throws Exception {
    JsonNode reply = synthetic.send(GTNetMessageCodeType.GT_NET_HISTORYQUOTE_EXCHANGE_SEL_C.getValue(), null);

    assertRefusedForWantOfAGrant(reply);
  }

  @Test
  @Order(3)
  void refusesAnExchangeSyncToAPeerWithoutAnAcceptedExchange() throws Exception {
    // The sync both serves our whole exchange inventory and writes the peer's supplier details, yet it used to check
    // nothing beyond the existence of a local entry.
    JsonNode reply = synthetic.send(GTNetMessageCodeType.GT_NET_EXCHANGE_SYNC_SEL_RR_C.getValue(), null);

    assertRefusedForWantOfAGrant(reply);
  }

  @Test
  @Order(4)
  void stillServesSecurityMetadataWhichIsNotAnExchange() throws Exception {
    var security = fixture.securities().get(0);

    JsonNode reply = synthetic.send(GTNetMessageCodeType.GT_NET_SECURITY_LOOKUP_SEL_C.getValue(),
        new SecurityLookupMsg(security.isin(), security.currency(), null));

    assertThat(reply.path("messageCode").asInt()).as("answer was %s", reply)
        .isEqualTo(GTNetMessageCodeType.GT_NET_SECURITY_LOOKUP_RESPONSE_S.getValue());
  }

  @Test
  @Order(5)
  void servesTheSamePeerOnceTheExchangeIsAccepted() throws Exception {
    synthetic.grantDataExchange(jwtB, "LAST_PRICE");

    JsonNode reply = synthetic.send(GTNetMessageCodeType.GT_NET_LASTPRICE_EXCHANGE_SEL_C.getValue(),
        lastpriceRequest());

    assertThat(reply.path("messageCode").asInt()).as("answer was %s", reply)
        .isEqualTo(GTNetMessageCodeType.GT_NET_LASTPRICE_EXCHANGE_RESPONSE_S.getValue());
  }

  /** One fixture instrument is enough: the grant is checked before anything is looked up. */
  private static LastpriceExchangeMsg lastpriceRequest() {
    var security = fixture.securities().get(0);
    InstrumentPriceDTO price = new InstrumentPriceDTO();
    price.setIsin(security.isin());
    price.setCurrency(security.currency());
    return LastpriceExchangeMsg.forRequest(List.of(price), List.of(), null);
  }

  private static void assertRefusedForWantOfAGrant(JsonNode reply) {
    assertThat(reply.path("messageCode").asInt()).as("answer was %s", reply).isEqualTo(ERROR);
    assertThat(reply.path("errorMsgCode").asString()).isEqualTo("NO_GRANT");
  }
}
