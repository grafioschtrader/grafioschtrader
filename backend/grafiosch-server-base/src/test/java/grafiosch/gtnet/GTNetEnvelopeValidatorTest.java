package grafiosch.gtnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetMessage;
import grafiosch.entities.GTNetMessage.GTNetMessageParam;
import grafiosch.gtnet.m2m.model.MessageEnvelope;
import grafiosch.repository.GTNetMessageJpaRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * Guards the bounds of an inbound envelope. Every one of them used to be enforced only by a column width, so the
 * failure was a DataException and an HTTP 500 rather than something the sending peer could read.
 */
class GTNetEnvelopeValidatorTest {

  private static final byte PING = GNetCoreMessageCode.GT_NET_PING.getValue();
  private static final byte MAINTENANCE_CANCEL = GNetCoreMessageCode.GT_NET_MAINTENANCE_CANCEL_ALL_C.getValue();
  private static final byte MAINTENANCE = GNetCoreMessageCode.GT_NET_MAINTENANCE_ALL_C.getValue();
  private static final byte HANDSHAKE_ACCEPT = GNetCoreMessageCode.GT_NET_FIRST_HANDSHAKE_ACCEPT_S.getValue();
  private static final Integer ID_PEER = 7;

  /** The real registry: what kind of message a code is has to be the protocol's answer, not a stub's. */
  private final GTNetMessageCodeRegistry messageCodeRegistry = new GTNetMessageCodeRegistry();
  private final GTNetMessageJpaRepository messageRepository = mock(GTNetMessageJpaRepository.class);
  private final GTNetEnvelopeValidator validator = new GTNetEnvelopeValidator(
      new GTNetProtocolLimits(4194304, 3, 512, 5, 7), messageCodeRegistry, new ObjectMapper(), messageRepository);

  @Test
  void acceptsAWellFormedEnvelope() {
    assertThat(validator.validate(envelope(PING), peer())).isEmpty();
  }

  @Test
  void refusesAnEnvelopeWithoutASourceDomain() {
    MessageEnvelope me = envelope(PING);
    me.sourceDomain = "  ";
    assertThat(errorOf(me)).isEqualTo("ENVELOPE_INVALID");
  }

  @Test
  void refusesAnEnvelopeWithoutASenderLocalId() {
    MessageEnvelope me = envelope(MAINTENANCE);
    me.idSourceGtNetMessage = null;
    assertThat(errorOf(me)).isEqualTo("ENVELOPE_INVALID");
  }

  @Test
  void acceptsAnEnvelopeWithoutASenderLocalIdWhenTheSenderKeepsNoMessage() {
    // The ping and the payload exchanges are built by a service and never written to gt_net_message, so there is no
    // local id to name. Demanding one refused every intraday price request, every historyquote exchange and every
    // liveness ping with ENVELOPE_INVALID.
    MessageEnvelope me = envelope(PING);
    me.idSourceGtNetMessage = null;
    assertThat(validator.validate(me, peer())).isEmpty();
  }

  @Test
  void refusesAMessageTextWiderThanItsColumn() {
    MessageEnvelope me = envelope(PING);
    me.message = "x".repeat(1001);
    assertThat(errorOf(me)).isEqualTo("ENVELOPE_INVALID");
  }

  @Test
  void refusesAVisibilityOutsideTheDeclaredRange() {
    // A byte outside {0, 1} used to be stored verbatim, which made the message invisible to everyone including
    // administrators, because the two read queries filter on the two known values.
    MessageEnvelope me = envelope(PING);
    me.visibility = 7;
    assertThat(errorOf(me)).isEqualTo("ENVELOPE_INVALID");
  }

  @Test
  void refusesAParameterValueWiderThanItsColumn() {
    MessageEnvelope me = envelope(PING);
    me.gtNetMessageParamMap = Map.of("a", new GTNetMessageParam("x".repeat(256)));
    assertThat(errorOf(me)).isEqualTo("ENVELOPE_INVALID");
  }

  @Test
  void refusesAParameterWithoutAValue() {
    MessageEnvelope me = envelope(PING);
    Map<String, GTNetMessageParam> params = new HashMap<>();
    params.put("a", new GTNetMessageParam(null));
    me.gtNetMessageParamMap = params;
    assertThat(errorOf(me)).isEqualTo("ENVELOPE_INVALID");
  }

  @Test
  void refusesMoreParametersThanTheProtocolAllows() {
    MessageEnvelope me = envelope(PING);
    me.gtNetMessageParamMap = Map.of("a", new GTNetMessageParam("1"), "b", new GTNetMessageParam("2"), "c",
        new GTNetMessageParam("3"), "d", new GTNetMessageParam("4"));
    assertThat(errorOf(me)).isEqualTo("ENVELOPE_INVALID");
  }

  @Test
  void refusesAPayloadLargerThanTheProtocolAllows() {
    MessageEnvelope me = envelope(PING);
    me.payload = new ObjectMapper().valueToTree(Map.of("blob", "x".repeat(1024)));
    assertThat(errorOf(me)).isEqualTo("PAYLOAD_TOO_LARGE");
  }

  @Test
  void refusesAnEnvelopeStampedInTheFuture() {
    MessageEnvelope me = envelope(PING);
    me.timestamp = LocalDateTime.now(ZoneOffset.UTC).plusMinutes(30);
    assertThat(errorOf(me)).isEqualTo("CLOCK_SKEW_EXCEEDED");
  }

  @Test
  void refusesAStaleEnvelopeOfACodeThatIsDeliveredOnce() {
    MessageEnvelope me = envelope(PING);
    me.timestamp = LocalDateTime.now(ZoneOffset.UTC).minusHours(2);
    assertThat(errorOf(me)).isEqualTo("CLOCK_SKEW_EXCEEDED");
  }

  @Test
  void acceptsAStaleEnvelopeOfACodeThatABackgroundTaskRedelivers() {
    // The delivery tasks rebuild the envelope from the persisted row, whose timestamp is its creation time, so a
    // retry hours later legitimately carries an old instant.
    MessageEnvelope me = envelope(MAINTENANCE);
    me.timestamp = LocalDateTime.now(ZoneOffset.UTC).minusDays(1);
    assertThat(validator.validate(me, peer())).isEmpty();
  }

  @Test
  void refusesAResponseThatNamesNoRequest() {
    MessageEnvelope me = envelope(HANDSHAKE_ACCEPT);
    assertThat(errorOf(me)).isEqualTo("ENVELOPE_INVALID");
  }

  @Test
  void refusesACorrelationThatPointsAtAnotherPeersMessage() {
    // storeIncomingResponseMessage writes this value straight into the local reply_to foreign key, so an unchecked
    // value would let one peer thread its answer under another peer's conversation.
    MessageEnvelope me = envelope(HANDSHAKE_ACCEPT);
    me.replyToSourceId = 42;
    when(messageRepository.findByIdGtNetMessage(anyInt())).thenReturn(messageOfPeer(99));
    assertThat(errorOf(me)).isEqualTo("ENVELOPE_INVALID");
  }

  @Test
  void acceptsACorrelationThatResolvesToAMessageOfTheSamePeer() {
    MessageEnvelope me = envelope(HANDSHAKE_ACCEPT);
    me.replyToSourceId = 42;
    when(messageRepository.findByIdGtNetMessage(anyInt())).thenReturn(messageOfPeer(ID_PEER));
    assertThat(validator.validate(me, peer())).isEmpty();
  }

  @Test
  void refusesACancellationThatNamesNoAnnouncement() {
    assertThat(errorOf(envelope(MAINTENANCE_CANCEL))).isEqualTo("ENVELOPE_INVALID");
  }

  @Test
  void refusesAnOriginalMessageOnACodeThatIsNoCancellation() {
    MessageEnvelope me = envelope(PING);
    me.idOriginalMessage = 5;
    assertThat(errorOf(me)).isEqualTo("ENVELOPE_INVALID");
  }

  private String errorOf(MessageEnvelope me) {
    return validator.validate(me, peer()).map(GTNetEnvelopeValidator.EnvelopeViolation::errorMsgCode).orElse(null);
  }

  private static MessageEnvelope envelope(byte messageCode) {
    MessageEnvelope me = new MessageEnvelope();
    me.sourceDomain = "http://192.0.2.10:8080";
    me.idSourceGtNetMessage = 11;
    me.timestamp = LocalDateTime.now(ZoneOffset.UTC);
    me.messageCode = messageCode;
    return me;
  }

  private static GTNet peer() {
    GTNet peer = new GTNet();
    peer.setIdGtNet(ID_PEER);
    return peer;
  }

  private static GTNetMessage messageOfPeer(Integer idGtNet) {
    GTNetMessage message = new GTNetMessage();
    message.setIdGtNet(idGtNet);
    return message;
  }
}
