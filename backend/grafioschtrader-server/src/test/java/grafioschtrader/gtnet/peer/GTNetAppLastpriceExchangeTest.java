package grafioschtrader.gtnet.peer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
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
import grafioschtrader.gtnet.m2m.model.InstrumentPriceDTO;
import grafioschtrader.gtnet.model.msg.LastpriceExchangeMsg;
import tools.jackson.databind.JsonNode;

/**
 * Intraday price exchange between two real application peers: request and response, push and acknowledgement, and the
 * only limit GTNet actually enforces.
 *
 * The requests are driven by a synthetic peer rather than through the UI, because the payload codes are programmatic:
 * the message dialog filters its options through getAvailableMessageCodes(), which offers core codes only, so 60 and 62
 * are not sendable from a browser at all.
 */
@TestMethodOrder(OrderAnnotation.class)
class GTNetAppLastpriceExchangeTest {

  private static final String SYNTHETIC_DOMAIN = "http://lastprice-probe.example:8099";

  private static GTNetAppPeerFixture fixture;
  private static SyntheticPeer synthetic;
  private static String jwtB;

  @BeforeAll
  static void connect() throws Exception {
    fixture = GTNetAppPeerFixture.load();
    jwtB = GTNetPeerTestSupport.loginAdmin(GTNetPeerTestSupport.PEER_B);
    synthetic = SyntheticPeer.connect(GTNetPeerTestSupport.PEER_B, SYNTHETIC_DOMAIN);
    // A completed handshake no longer entitles a peer to price data: serving it needs an accepted,
    // unrevoked grant for this peer and this kind. The synthetic peer asks for one the way a real peer does.
    synthetic.grantDataExchange(jwtB, "LAST_PRICE");
  }

  @AfterAll
  static void removeSyntheticPeer() throws Exception {
    if (synthetic != null) {
      synthetic.disconnect(jwtB);
    }
  }

  @Test
  @Order(1)
  void anExchangeRequestIsAnsweredWithPricesForTheRequestedInstruments() throws Exception {
    LastpriceExchangeMsg request = LastpriceExchangeMsg.forRequest(securityRequests(fixture.securities().size()),
        currencypairRequests(), null);

    JsonNode reply = synthetic.send(GTNetMessageCodeType.GT_NET_LASTPRICE_EXCHANGE_SEL_C.getValue(), request);

    assertThat(reply.path("messageCode").asInt())
        .isEqualTo(GTNetMessageCodeType.GT_NET_LASTPRICE_EXCHANGE_RESPONSE_S.getValue());
    assertThat(reply.path("payload").isMissingNode()).isFalse();
  }

  @Test
  @Order(2)
  void aPushIsAcknowledgedWithTheAcceptedCount() throws Exception {
    LastpriceExchangeMsg push = new LastpriceExchangeMsg();
    for (var security : fixture.securities()) {
      InstrumentPriceDTO price = new InstrumentPriceDTO();
      price.setIsin(security.isin());
      price.setCurrency(security.currency());
      price.setTimestamp(LocalDateTime.now());
      price.setLast(101.5);
      push.securities.add(price);
    }

    JsonNode reply = synthetic.send(GTNetMessageCodeType.GT_NET_LASTPRICE_PUSH_SEL_C.getValue(), push);

    assertThat(reply.path("messageCode").asInt())
        .isEqualTo(GTNetMessageCodeType.GT_NET_LASTPRICE_PUSH_ACK_S.getValue());
    LastpriceExchangeMsg ack = GTNetPeerTestSupport.JSON.treeToValue(reply.path("payload"), LastpriceExchangeMsg.class);
    assertThat(ack.acceptedCount).isNotNull();
  }

  @Test
  @Order(3)
  void aRequestBeyondMaxLimitIsRefusedAndCountedAsAViolation() throws Exception {
    int before = violationCount();

    LastpriceExchangeMsg oversized = LastpriceExchangeMsg.forRequest(securityRequests(fixture.maxLimitProbeCount()),
        List.of(), null);
    JsonNode reply = synthetic.send(GTNetMessageCodeType.GT_NET_LASTPRICE_EXCHANGE_SEL_C.getValue(), oversized);

    assertThat(reply.path("messageCode").asInt())
        .isEqualTo(GTNetMessageCodeType.GT_NET_LASTPRICE_MAX_LIMIT_EXCEEDED_S.getValue());
    assertThat(reply.path("message").asText()).contains("max_limit");
    assertThat(violationCount()).isEqualTo(before + 1);
  }

  @Test
  @Order(4)
  void anExhaustedViolationBudgetRefusesEvenALegitimateRequest() throws Exception {
    setViolationCount(99);

    LastpriceExchangeMsg request = LastpriceExchangeMsg.forRequest(securityRequests(1), List.of(), null);
    JsonNode reply = synthetic.send(GTNetMessageCodeType.GT_NET_LASTPRICE_EXCHANGE_SEL_C.getValue(), request);

    assertThat(reply.path("messageCode").asInt())
        .isEqualTo(GTNetMessageCodeType.GT_NET_LASTPRICE_MAX_LIMIT_EXCEEDED_S.getValue());
    assertThat(reply.path("message").asText()).contains("max_limit violations");
    // Refusing must not raise the counter any further; it already saturated.
    assertThat(violationCount()).isEqualTo(99);

    // Clearing the counter is the administrator action that admits the peer again.
    setViolationCount(0);
    JsonNode afterReset = synthetic.send(GTNetMessageCodeType.GT_NET_LASTPRICE_EXCHANGE_SEL_C.getValue(), request);
    assertThat(afterReset.path("messageCode").asInt())
        .isEqualTo(GTNetMessageCodeType.GT_NET_LASTPRICE_EXCHANGE_RESPONSE_S.getValue());
  }

  /**
   * Builds as many security requests as asked for. Beyond the fixture rows the ISINs are synthetic, which is exactly
   * what a max_limit probe needs: the count is checked before anything is looked up.
   *
   * @param count how many instruments the request should carry
   * @return the request DTOs
   */
  private static List<InstrumentPriceDTO> securityRequests(int count) {
    List<InstrumentPriceDTO> requests = new ArrayList<>();
    var securities = fixture.securities();
    for (int i = 0; i < count; i++) {
      InstrumentPriceDTO price = new InstrumentPriceDTO();
      if (i < securities.size()) {
        price.setIsin(securities.get(i).isin());
        price.setCurrency(securities.get(i).currency());
      } else {
        price.setIsin(String.format("XX%010d", i));
        price.setCurrency("CHF");
      }
      requests.add(price);
    }
    return requests;
  }

  private static List<InstrumentPriceDTO> currencypairRequests() {
    List<InstrumentPriceDTO> requests = new ArrayList<>();
    for (var pair : fixture.currencypairs()) {
      InstrumentPriceDTO price = new InstrumentPriceDTO();
      price.setCurrency(pair.fromCurrency());
      price.setToCurrency(pair.toCurrency());
      requests.add(price);
    }
    return requests;
  }

  private static JsonNode syntheticEntry() throws Exception {
    for (JsonNode entry : GTNetPeerTestSupport.readGTNet(GTNetPeerTestSupport.PEER_B, jwtB).path("gtNetList")) {
      if (SYNTHETIC_DOMAIN.equals(entry.path("domainRemoteName").asText())) {
        return entry;
      }
    }
    throw new IllegalStateException("Synthetic peer " + SYNTHETIC_DOMAIN + " is missing at peer B");
  }

  private static int violationCount() throws Exception {
    return syntheticEntry().path("gtNetConfig").path("requestViolationCount").asInt();
  }

  /** Writes the counter the way an administrator does, through the ADMIN-only GTNetConfig endpoint. */
  private static void setViolationCount(int value) throws Exception {
    var config = ((tools.jackson.databind.node.ObjectNode) syntheticEntry().path("gtNetConfig")).deepCopy();
    config.put("requestViolationCount", value);
    var response = GTNetPeerTestSupport.putApi(GTNetPeerTestSupport.PEER_B, "/api/gtnetconfig", jwtB,
        config.toString());
    assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
    assertThat(violationCount()).isEqualTo(value);
  }
}
