package grafioschtrader.gtnet.peer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
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
import grafioschtrader.gtnet.m2m.model.InstrumentHistoryquoteDTO;
import grafioschtrader.gtnet.model.msg.HistoryquoteCoverageQueryMsg;
import grafioschtrader.gtnet.model.msg.HistoryquoteCoverageQueryMsg.InstrumentIdentifier;
import grafioschtrader.gtnet.model.msg.HistoryquoteCoverageResponseMsg;
import grafioschtrader.gtnet.model.msg.HistoryquoteExchangeMsg;
import tools.jackson.databind.JsonNode;

/**
 * Historical price exchange between two real application peers: the date-range exchange, the push and its
 * acknowledgement, the max_limit refusal, and the coverage query that lets a requester choose a peer before asking for
 * any data.
 *
 * Coverage (85/86) is the pair with no UI representation at all - the frontend message-code enum stops at 93 - so a
 * synthetic peer is the only way to drive it.
 */
@TestMethodOrder(OrderAnnotation.class)
class GTNetAppHistoryquoteExchangeTest {

  private static final String SYNTHETIC_DOMAIN = "http://historyquote-probe.example:8099";

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
    synthetic.grantDataExchange(jwtB, "HISTORICAL_PRICES");
  }

  @AfterAll
  static void removeSyntheticPeer() throws Exception {
    if (synthetic != null) {
      synthetic.disconnect(jwtB);
    }
  }

  @Test
  @Order(1)
  void aCoverageQueryReportsWhatThePeerCanDeliverWithoutSendingPrices() throws Exception {
    List<InstrumentIdentifier> known = new ArrayList<>();
    for (var security : fixture.securities()) {
      known.add(InstrumentIdentifier.forSecurity(security.isin(), security.currency()));
    }
    var unknown = fixture.unknownSecurity();
    known.add(InstrumentIdentifier.forSecurity(unknown.isin(), unknown.currency()));

    JsonNode reply = synthetic.send(GTNetMessageCodeType.GT_NET_HISTORYQUOTE_COVERAGE_SEL_C.getValue(),
        HistoryquoteCoverageQueryMsg.forQuery(known, List.of()));

    assertThat(reply.path("messageCode").asInt())
        .isEqualTo(GTNetMessageCodeType.GT_NET_HISTORYQUOTE_COVERAGE_RESPONSE_S.getValue());
    HistoryquoteCoverageResponseMsg coverage = GTNetPeerTestSupport.JSON.treeToValue(reply.path("payload"),
        HistoryquoteCoverageResponseMsg.class);
    assertThat(coverage.securities).hasSize(known.size());
    // The instrument the peer does not know is reported explicitly rather than omitted, so the requester can tell
    // "no data" from "not asked".
    assertThat(coverage.securities.get(coverage.securities.size() - 1).isAvailable()).isFalse();
  }

  @Test
  @Order(2)
  void anExchangeRequestForADateRangeIsAnswered() throws Exception {
    LocalDate from = fixture.historyFromDate();
    LocalDate to = fixture.historyToDate();
    List<InstrumentHistoryquoteDTO> securities = new ArrayList<>();
    for (var security : fixture.securities()) {
      securities.add(InstrumentHistoryquoteDTO.forSecurityRequest(security.isin(), security.currency(), from, to));
    }

    JsonNode reply = synthetic.send(GTNetMessageCodeType.GT_NET_HISTORYQUOTE_EXCHANGE_SEL_C.getValue(),
        HistoryquoteExchangeMsg.forRequest(securities, List.of()));

    assertThat(reply.path("messageCode").asInt())
        .isEqualTo(GTNetMessageCodeType.GT_NET_HISTORYQUOTE_EXCHANGE_RESPONSE_S.getValue());
    assertThat(reply.path("payload").isMissingNode()).isFalse();
  }

  @Test
  @Order(3)
  void aPushIsAcknowledged() throws Exception {
    LocalDate from = fixture.historyFromDate();
    var security = fixture.securities().get(0);
    HistoryquoteExchangeMsg push = new HistoryquoteExchangeMsg();
    push.securities.add(
        InstrumentHistoryquoteDTO.forSecurityRequest(security.isin(), security.currency(), from, from.plusDays(1)));

    JsonNode reply = synthetic.send(GTNetMessageCodeType.GT_NET_HISTORYQUOTE_PUSH_SEL_C.getValue(), push);

    assertThat(reply.path("messageCode").asInt())
        .isEqualTo(GTNetMessageCodeType.GT_NET_HISTORYQUOTE_PUSH_ACK_S.getValue());
  }

  @Test
  @Order(4)
  void aRequestBeyondMaxLimitIsRefused() throws Exception {
    LocalDate from = fixture.historyFromDate();
    LocalDate to = fixture.historyToDate();
    List<InstrumentHistoryquoteDTO> oversized = new ArrayList<>();
    for (int i = 0; i < fixture.maxLimitProbeCount(); i++) {
      oversized.add(InstrumentHistoryquoteDTO.forSecurityRequest(String.format("XX%010d", i), "CHF", from, to));
    }

    JsonNode reply = synthetic.send(GTNetMessageCodeType.GT_NET_HISTORYQUOTE_EXCHANGE_SEL_C.getValue(),
        HistoryquoteExchangeMsg.forRequest(oversized, List.of()));

    assertThat(reply.path("messageCode").asInt())
        .isEqualTo(GTNetMessageCodeType.GT_NET_HISTORYQUOTE_MAX_LIMIT_EXCEEDED_S.getValue());
    assertThat(reply.path("message").asText()).contains("max_limit");
  }

  @Test
  @Order(5)
  void aCoverageQueryBeyondMaxLimitIsRefusedWithTheSameCode() throws Exception {
    List<InstrumentIdentifier> oversized = new ArrayList<>();
    for (int i = 0; i < fixture.maxLimitProbeCount(); i++) {
      oversized.add(InstrumentIdentifier.forSecurity(String.format("XX%010d", i), "CHF"));
    }

    JsonNode reply = synthetic.send(GTNetMessageCodeType.GT_NET_HISTORYQUOTE_COVERAGE_SEL_C.getValue(),
        HistoryquoteCoverageQueryMsg.forQuery(oversized, List.of()));

    assertThat(reply.path("messageCode").asInt())
        .isEqualTo(GTNetMessageCodeType.GT_NET_HISTORYQUOTE_MAX_LIMIT_EXCEEDED_S.getValue());
  }
}
