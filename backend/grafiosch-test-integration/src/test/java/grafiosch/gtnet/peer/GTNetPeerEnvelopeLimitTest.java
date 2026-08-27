package grafiosch.gtnet.peer;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import grafiosch.test.gtnet.GTNetPeerTestSupport;
import grafiosch.test.gtnet.SyntheticPeer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Drives peer B with envelopes a well-behaved client never produces.
 *
 * <p>
 * Every one of these used to be bounded only by a database column, so the failure was a DataException and an HTTP 500
 * rather than something the sending peer could read. They are refused with a stable {@code errorMsgCode} now, and the
 * HTTP status stays 200 because the envelope is the protocol - a non-2xx is discarded by {@code BaseDataClient} and the
 * reason would be lost with it.
 * </p>
 */
class GTNetPeerEnvelopeLimitTest {

  /** RFC 2606 reserved name; peer B stores it as an ordinary remote entry and never dials it. */
  private static final String SYNTHETIC_DOMAIN = "http://envelope-limit-probe.example:8099";
  private static final byte PING = 0;
  private static final byte ERROR = 23;
  private static final URI PEER = GTNetPeerTestSupport.PEER_B;

  private static String jwtB;
  private static SyntheticPeer synthetic;

  @BeforeAll
  static void connect() throws Exception {
    jwtB = GTNetPeerTestSupport.loginAdmin(PEER);
    synthetic = SyntheticPeer.connect(PEER, SYNTHETIC_DOMAIN);
  }

  @AfterAll
  static void disconnect() throws Exception {
    if (synthetic != null) {
      synthetic.disconnect(jwtB);
    }
  }

  @Test
  void refusesAMessageTextWiderThanItsColumn() throws Exception {
    ObjectNode envelope = synthetic.newEnvelope(PING);
    envelope.put("message", "x".repeat(1001));

    assertRefusedWith(synthetic.sendRaw(envelope), "ENVELOPE_INVALID");
  }

  @Test
  void refusesAParameterValueWiderThanItsColumn() throws Exception {
    ObjectNode envelope = synthetic.newEnvelope(PING);
    envelope.putObject("gtNetMessageParamMap").putObject("probe").put("paramValue", "x".repeat(256));

    assertRefusedWith(synthetic.sendRaw(envelope), "ENVELOPE_INVALID");
  }

  @Test
  void refusesMoreParametersThanTheProtocolAllows() throws Exception {
    ObjectNode envelope = synthetic.newEnvelope(PING);
    ObjectNode params = envelope.putObject("gtNetMessageParamMap");
    for (int i = 0; i <= 64; i++) {
      params.putObject("p" + i).put("paramValue", String.valueOf(i));
    }

    assertRefusedWith(synthetic.sendRaw(envelope), "ENVELOPE_INVALID");
  }

  @Test
  void refusesAVisibilityOutsideTheDeclaredRange() throws Exception {
    // A byte outside the declared range used to be stored verbatim, which made the message invisible to everyone
    // including administrators, because both read queries filter on the two known values.
    ObjectNode envelope = synthetic.newEnvelope(PING);
    envelope.put("visibility", 7);

    assertRefusedWith(synthetic.sendRaw(envelope), "ENVELOPE_INVALID");
  }

  @Test
  void refusesAnEnvelopeDatedOutsideTheAcceptedSkew() throws Exception {
    ObjectNode envelope = synthetic.newEnvelope(PING);
    envelope.put("timestamp",
        LocalDateTime.now(ZoneOffset.UTC).plusHours(3).truncatedTo(ChronoUnit.SECONDS).toString());

    assertRefusedWith(synthetic.sendRaw(envelope), "CLOCK_SKEW_EXCEEDED");
  }

  @Test
  void refusesAnEnvelopeWithoutASenderLocalId() throws Exception {
    ObjectNode envelope = synthetic.newEnvelope(PING);
    envelope.remove("idSourceGtNetMessage");

    assertRefusedWith(synthetic.sendRaw(envelope), "ENVELOPE_INVALID");
  }

  @Test
  void refusesABodyThatIsNotReadableAsAnEnvelope() throws Exception {
    var response = GTNetPeerTestSupport.postJson(PEER, "/m2m/gtnet", synthetic.getToken(), "{\"messageCode\":");

    // A body Jackson cannot bind never reaches the endpoint, so the answer comes from the M2M exception advice - and
    // it is still an envelope, not a Spring error page.
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    assertThat(response.body()).contains("ENVELOPE_INVALID");
  }

  @Test
  void acceptsAnOrdinaryEnvelopeOfTheSameShape() throws Exception {
    assertThat(synthetic.sendRaw(synthetic.newEnvelope(PING)).path("messageCode").asInt()).isNotEqualTo(ERROR);
  }

  private static void assertRefusedWith(JsonNode reply, String errorMsgCode) {
    assertThat(reply.path("messageCode").asInt()).as("answer was %s", reply).isEqualTo(ERROR);
    assertThat(reply.path("errorMsgCode").asString()).isEqualTo(errorMsgCode);
  }
}
