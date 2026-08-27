package grafiosch.gtnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetMessage;
import grafiosch.entities.GTNetMessage.GTNetMessageParam;
import grafiosch.gtnet.m2m.model.MessageEnvelope;
import grafiosch.repository.GTNetMessageJpaRepository;

/**
 * Guards how a repeated delivery is recognized and answered. Redelivery is a normal event: both delivery tasks re-send
 * the same persisted message and the envelope carries its id unchanged, so a retry after a lost response is
 * byte-identical.
 */
class GTNetIdempotencyServiceTest {

  private static final byte DATA_REQUEST = GNetCoreMessageCode.GT_NET_DATA_REQUEST_SEL_RR_C.getValue();
  private static final byte MAINTENANCE = GNetCoreMessageCode.GT_NET_MAINTENANCE_ALL_C.getValue();
  private static final byte TOKEN_REFRESH_ACCEPT = GNetCoreMessageCode.GT_NET_TOKEN_REFRESH_ACCEPT_S.getValue();
  private static final Integer ID_PEER = 7;
  private static final Integer SENDER_LOCAL_ID = 11;
  private static final Integer OUR_LOCAL_ID = 500;

  private final GTNetMessageJpaRepository messageRepository = mock(GTNetMessageJpaRepository.class);
  /** The real registry, because what a code is has to be the protocol's answer and not a stub's. */
  private final GTNetMessageCodeRegistry messageCodeRegistry = new GTNetMessageCodeRegistry();
  private final GTNetIdempotencyService idempotencyService = new GTNetIdempotencyService(messageRepository,
      messageCodeRegistry);

  @Test
  void recognizesARepeatByThePeerScopedTriple() {
    GTNetMessage previous = receivedRow(DATA_REQUEST);
    when(messageRepository.findByIdGtNetAndSendRecvAndIdSourceGtNetMessage(ID_PEER,
        SendReceivedType.RECEIVED.getValue(), SENDER_LOCAL_ID)).thenReturn(Optional.of(previous));

    assertThat(idempotencyService.findPreviousDelivery(peer(), envelope(DATA_REQUEST))).contains(previous);
  }

  @Test
  void treatsAFirstDeliveryAsNew() {
    when(messageRepository.findByIdGtNetAndSendRecvAndIdSourceGtNetMessage(anyInt(), anyByte(), anyInt()))
        .thenReturn(Optional.empty());

    assertThat(idempotencyService.findPreviousDelivery(peer(), envelope(DATA_REQUEST))).isEmpty();
  }

  @Test
  void cannotIdentifyADeliveryFromAnUnknownPeer() {
    // The first handshake reaches us before any peer row exists, so there is nothing to key on. Its own
    // HANDSHAKE_ALREADY_ESTABLISHED is what makes a repeat deterministic there.
    assertThat(idempotencyService.findPreviousDelivery(null, envelope(DATA_REQUEST))).isEmpty();
  }

  @Test
  void answersARepeatedRequestWithTheAnswerItAlreadyGot() {
    GTNetMessage previous = receivedRow(DATA_REQUEST);
    GTNetMessage answer = sentAnswer(TOKEN_REFRESH_ACCEPT, Map.of("tokenThis", new GTNetMessageParam("abc")));
    when(messageRepository.findByReplyTo(OUR_LOCAL_ID)).thenReturn(List.of(answer));

    MessageEnvelope replay = idempotencyService.replayOutcome(myGTNet(), previous, DATA_REQUEST);

    // The parameters come back with it, which is what makes a repeated token refresh return the same token pair
    // instead of rotating again.
    assertThat(replay.messageCode).isEqualTo(TOKEN_REFRESH_ACCEPT);
    assertThat(replay.gtNetMessageParamMap.get("tokenThis").getParamValue()).isEqualTo("abc");
  }

  @Test
  void tellsARepeatedRequestStillAwaitingADecisionThatItIsDeferred() {
    when(messageRepository.findByReplyTo(anyInt())).thenReturn(List.of());

    MessageEnvelope replay = idempotencyService.replayOutcome(myGTNet(), receivedRow(DATA_REQUEST), DATA_REQUEST);

    assertThat(replay.messageCode).isEqualTo(GNetCoreMessageCode.GT_NET_DEFERRED_S.getValue());
    assertThat(replay.errorMsgCode).isEqualTo(GTNetIdempotencyService.DUPLICATE_DELIVERY);
  }

  @Test
  void acknowledgesARepeatedAnnouncementWithoutRepeatingIt() {
    when(messageRepository.findByReplyTo(anyInt())).thenReturn(List.of());

    MessageEnvelope replay = idempotencyService.replayOutcome(myGTNet(), receivedRow(MAINTENANCE), MAINTENANCE);

    assertThat(replay.messageCode).isEqualTo(GNetCoreMessageCode.GT_NET_ACK_S.getValue());
    assertThat(replay.errorMsgCode).isEqualTo(GTNetIdempotencyService.DUPLICATE_DELIVERY);
  }

  @Test
  void ignoresAnInboundRowWhenLookingForTheAnswerWeSent() {
    // findByReplyTo returns whatever hangs off the request; only the row we sent is the answer the peer received.
    GTNetMessage inboundNoise = receivedRow(DATA_REQUEST);
    when(messageRepository.findByReplyTo(anyInt())).thenReturn(List.of(inboundNoise));

    MessageEnvelope replay = idempotencyService.replayOutcome(myGTNet(), receivedRow(DATA_REQUEST), DATA_REQUEST);

    assertThat(replay.messageCode).isEqualTo(GNetCoreMessageCode.GT_NET_DEFERRED_S.getValue());
  }

  @Test
  void letsASideEffectFreeQueryRunAgainRatherThanReplayingAnEmptyPayload() {
    // The server list request reads and changes nothing, so the protocol declares it reprocessable; the data request
    // writes a grant and must be replayed from its stored outcome instead.
    assertThat(idempotencyService.prefersReprocessing(GNetCoreMessageCode.GT_NET_UPDATE_SERVERLIST_SEL_RR_C.getValue()))
        .isTrue();
    assertThat(idempotencyService.prefersReprocessing(DATA_REQUEST)).isFalse();
  }

  private static MessageEnvelope envelope(byte messageCode) {
    MessageEnvelope me = new MessageEnvelope();
    me.sourceDomain = "http://192.0.2.10:8080";
    me.idSourceGtNetMessage = SENDER_LOCAL_ID;
    me.timestamp = LocalDateTime.now(ZoneOffset.UTC);
    me.messageCode = messageCode;
    return me;
  }

  private static GTNetMessage receivedRow(byte messageCode) {
    GTNetMessage message = new GTNetMessage(ID_PEER, LocalDateTime.now(ZoneOffset.UTC),
        SendReceivedType.RECEIVED.getValue(), null, messageCode, null, null);
    message.setIdGtNetMessage(OUR_LOCAL_ID);
    message.setIdSourceGtNetMessage(SENDER_LOCAL_ID);
    return message;
  }

  private static GTNetMessage sentAnswer(byte messageCode, Map<String, GTNetMessageParam> params) {
    return new GTNetMessage(ID_PEER, LocalDateTime.now(ZoneOffset.UTC), SendReceivedType.SEND.getValue(), OUR_LOCAL_ID,
        messageCode, null, params);
  }

  private static GTNet myGTNet() {
    GTNet myGTNet = new GTNet();
    myGTNet.setIdGtNet(1);
    myGTNet.setDomainRemoteName("http://192.0.2.20:8080");
    return myGTNet;
  }

  private static GTNet peer() {
    GTNet peer = new GTNet();
    peer.setIdGtNet(ID_PEER);
    return peer;
  }
}
