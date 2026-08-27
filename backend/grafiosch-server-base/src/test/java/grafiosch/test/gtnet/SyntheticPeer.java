package grafiosch.test.gtnet;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A GTNet peer played by the test itself over the raw wire.
 *
 * Several protocol paths cannot be driven any other way. The programmatic payload codes are never offered by the
 * message dialog, the exchange handlers answer a request rather than initiate one, and a client that did not perform
 * the handshake itself can never learn the token it has to send, because both token fields are JsonIgnore on the entity
 * and travel only as parameters of the handshake exchange.
 *
 * The synthetic domain is never dialled back: BaseDataClient.getActuatorInfo runs only on the very first gt_net insert,
 * and a peer under test already has rows by the time this is used. Delete the entry again when done.
 */
public final class SyntheticPeer {

  private static final byte FIRST_HANDSHAKE = 1;
  private static final byte FIRST_HANDSHAKE_ACCEPT = 2;
  private static final byte DATA_REQUEST = 50;
  private static final byte DATA_REQUEST_ACCEPT = 52;
  /** High enough not to collide with a rule a test of its own installs. */
  private static final int AUTO_ACCEPT_RULE_PRIORITY = 47;

  private final URI peer;
  private final String domain;
  private String token;
  private int envelopeIds = 9000;

  private SyntheticPeer(URI peer, String domain) {
    this.peer = peer;
    this.domain = domain;
  }

  /**
   * Registers a synthetic peer at the given instance and completes the handshake, so the returned object holds the
   * token that instance expects from it.
   *
   * @param peer   base URL of the instance to connect to
   * @param domain the address this synthetic peer announces; must be a reserved documentation name
   * @return a connected synthetic peer
   */
  public static SyntheticPeer connect(URI peer, String domain) throws Exception {
    SyntheticPeer synthetic = new SyntheticPeer(peer, domain);
    ObjectNode handshake = synthetic.envelope(FIRST_HANDSHAKE);
    handshake.putObject("gtNetMessageParamMap").putObject("tokenThis").put("paramValue", UUID.randomUUID().toString());
    ObjectNode payload = handshake.putObject("payload");
    payload.put("domainRemoteName", domain);
    payload.put("timeZone", "UTC");
    payload.put("spreadCapability", false);
    payload.put("dailyRequestLimit", 5000);
    payload.put("serverBusy", false);
    payload.put("allowServerCreation", false);
    payload.putArray("gtNetEntities");

    JsonNode reply = synthetic.post(handshake, null);
    assertThat(reply.path("messageCode").asInt()).as("handshake of %s was accepted", domain)
        .isEqualTo(FIRST_HANDSHAKE_ACCEPT);
    synthetic.token = reply.path("gtNetMessageParamMap").path("tokenThis").path("paramValue").asText();
    assertThat(synthetic.token).isNotBlank();
    return synthetic;
  }

  /**
   * Sends an envelope carrying a typed payload and returns the answer.
   *
   * @param messageCode byte value of the message code to send
   * @param payload     the payload object, serialized into the envelope; null sends no payload
   * @return the answering envelope
   */
  public JsonNode send(byte messageCode, Object payload) throws Exception {
    ObjectNode envelope = envelope(messageCode);
    if (payload != null) {
      envelope.set("payload", GTNetPeerTestSupport.JSON.valueToTree(payload));
    }
    return post(envelope, token);
  }

  /**
   * Builds an envelope of the given code without sending it, so a test can hand-write the parts a well-behaved client
   * would never produce - an over-long text, more parameters than the protocol allows, a skewed timestamp, a
   * correlation that points at another peer.
   *
   * @param messageCode byte value of the message code
   * @return a mutable envelope, ready to be handed to {@link #sendRaw(ObjectNode)}
   */
  public ObjectNode newEnvelope(byte messageCode) {
    return envelope(messageCode);
  }

  /**
   * Sends an envelope exactly as given, authenticated with this peer's token.
   *
   * <p>
   * {@link #send(byte, Object)} allocates a fresh {@code idSourceGtNetMessage} on every call, so it can never produce
   * the byte-identical retry that a redelivery is. Posting the same object twice through this method can.
   * </p>
   *
   * @param envelope the envelope to post
   * @return the answering envelope
   */
  public JsonNode sendRaw(ObjectNode envelope) throws Exception {
    return post(envelope, token);
  }

  /**
   * Sends an envelope without an authentication token, for the paths that must refuse an unauthenticated caller.
   *
   * @param envelope the envelope to post
   * @return the answering envelope
   */
  public JsonNode sendRawUnauthenticated(ObjectNode envelope) throws Exception {
    return post(envelope, null);
  }

  /** @return the token this instance expects from this synthetic peer */
  public String getToken() {
    return token;
  }

  /**
   * Obtains an accepted data exchange for the named kinds, the way a real peer does.
   *
   * <p>
   * Serving last prices or historical quotes needs an accepted, unrevoked grant for this peer and that kind, on top of
   * the instance's own accept flag; a completed handshake alone entitles a peer to nothing. The grant is created by
   * sending a data request and having it accepted, so an auto-answer rule is installed for the duration of the request
   * and removed again - leaving the instance's answering policy as it was found.
   * </p>
   *
   * @param jwt         an administrator token of the instance this peer is connected to
   * @param entityKinds the names of the exchange kinds to request
   */
  public void grantDataExchange(String jwt, String... entityKinds) throws Exception {
    Integer ruleId = installAutoAcceptRule(jwt);
    try {
      ObjectNode request = envelope(DATA_REQUEST);
      var kinds = request.putObject("payload").putArray("entityKinds");
      for (String entityKind : entityKinds) {
        kinds.add(entityKind);
      }
      JsonNode reply = post(request, token);
      assertThat(reply.path("messageCode").asInt()).as("data request of %s was accepted, answer was %s", domain, reply)
          .isEqualTo(DATA_REQUEST_ACCEPT);
    } finally {
      if (ruleId != null) {
        GTNetPeerTestSupport.deleteApi(peer, "/api/gtnetmessageanswer/" + ruleId, jwt);
      }
    }
  }

  private Integer installAutoAcceptRule(String jwt) throws Exception {
    ObjectNode rule = GTNetPeerTestSupport.JSON.createObjectNode();
    rule.put("requestMsgCode", "GT_NET_DATA_REQUEST_SEL_RR_C");
    rule.put("responseMsgCode", "GT_NET_DATA_REQUEST_ACCEPT_S");
    rule.put("priority", AUTO_ACCEPT_RULE_PRIORITY);
    rule.put("waitDaysApply", 0);
    var response = GTNetPeerTestSupport.postApi(peer, "/api/gtnetmessageanswer", jwt, rule.toString());
    assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
    return GTNetPeerTestSupport.JSON.readTree(response.body()).path("idGtNetMessageAnswer").asInt();
  }

  /** Removes this synthetic peer from the instance it connected to; safe to call when it was never created. */
  public void disconnect(String jwt) throws Exception {
    for (JsonNode entry : GTNetPeerTestSupport.readGTNet(peer, jwt).path("gtNetList")) {
      if (domain.equals(entry.path("domainRemoteName").asText())) {
        GTNetPeerTestSupport.deleteApi(peer, "/api/gtnet/" + entry.path("idGtNet").asInt(), jwt);
        return;
      }
    }
  }

  /** @return the address this synthetic peer announces, which is how it is found in the gt_net table */
  public String getDomain() {
    return domain;
  }

  private JsonNode post(ObjectNode envelope, String authToken) throws Exception {
    var response = GTNetPeerTestSupport.postJson(peer, "/m2m/gtnet", authToken, envelope.toString());
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    return GTNetPeerTestSupport.JSON.readTree(response.body());
  }

  private ObjectNode envelope(byte messageCode) {
    ObjectNode envelope = GTNetPeerTestSupport.JSON.createObjectNode();
    envelope.put("sourceDomain", domain);
    envelope.put("idSourceGtNetMessage", ++envelopeIds);
    envelope.put("timestamp", LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString());
    envelope.put("messageCode", messageCode);
    envelope.put("serverBusy", false);
    envelope.put("visibility", 0);
    return envelope;
  }
}
