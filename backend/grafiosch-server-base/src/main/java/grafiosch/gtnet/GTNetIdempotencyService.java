package grafiosch.gtnet;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.m2m.model.MessageEnvelope;
import grafiosch.repository.GTNetMessageJpaRepository;

/**
 * Recognizes a message that has already been delivered and answers it from what the first delivery produced.
 *
 * <p>
 * Redelivery is a normal event rather than an attack: both delivery tasks re-send the same persisted
 * {@code GTNetMessage}, and the envelope carries its id unchanged, so a retry after a lost response is byte-identical.
 * Without this the second delivery would insert a second row, repeat the handler's side effects and charge the peer's
 * daily budget again.
 * </p>
 *
 * <p>
 * The identity is the peer-scoped triple {@code (id_gt_net, send_recv, id_source_gt_net_message)}, backed by the unique
 * key {@code uk_gt_net_message_source}. The sender-local id alone is not an identity: it is only unique within the
 * sending instance.
 * </p>
 *
 * <p>
 * The first handshake is outside this mechanism. It has no peer row and no stored message until it is accepted, so
 * there is nothing to key on; a repeat is refused deterministically by the handler's own
 * {@code HANDSHAKE_ALREADY_ESTABLISHED}, which is its idempotency.
 * </p>
 */
@Service
public class GTNetIdempotencyService {

  /** Marks every answer that was produced by recognizing a repeat rather than by processing it again. */
  public static final String DUPLICATE_DELIVERY = "DUPLICATE_DELIVERY";

  private final GTNetMessageJpaRepository gtNetMessageJpaRepository;

  private final GTNetMessageCodeRegistry messageCodeRegistry;

  /**
   * @param gtNetMessageJpaRepository the message repository
   * @param messageCodeRegistry       the protocol registry, which answers what kind of message a code is
   */
  public GTNetIdempotencyService(GTNetMessageJpaRepository gtNetMessageJpaRepository,
      GTNetMessageCodeRegistry messageCodeRegistry) {
    this.gtNetMessageJpaRepository = gtNetMessageJpaRepository;
    this.messageCodeRegistry = messageCodeRegistry;
  }

  /**
   * The row an earlier delivery of this envelope created.
   *
   * @param remoteGTNet the local entry of the sending peer, null when the peer is not known yet
   * @param me          the received envelope
   * @return the row of the first delivery, empty when this envelope has not been seen before
   */
  public Optional<GTNetMessage> findPreviousDelivery(GTNet remoteGTNet, MessageEnvelope me) {
    if (remoteGTNet == null || me.idSourceGtNetMessage == null) {
      return Optional.empty();
    }
    return gtNetMessageJpaRepository.findByIdGtNetAndSendRecvAndIdSourceGtNetMessage(remoteGTNet.getIdGtNet(),
        SendReceivedType.RECEIVED.getValue(), me.idSourceGtNetMessage);
  }

  /**
   * Rebuilds the answer the peer was given the first time.
   *
   * <p>
   * A request that was answered has its answer stored with {@code reply_to} pointing at the request row, so the answer
   * is reproduced from that row — parameters included, which is what makes a repeated token refresh return the same
   * token pair. A request that is still awaiting an administrator gets the same deferred acknowledgement it got the
   * first time, and an announcement gets an acknowledgement carrying {@link #DUPLICATE_DELIVERY}, so the sender can
   * tell a recognized repeat apart from a fresh receipt.
   * </p>
   *
   * <p>
   * What cannot be reproduced is the optional JSON payload of the original answer: {@code gt_net_message} stores no
   * payload. That is why a handler whose answer carries one declares itself a side-effect-free query and is run again
   * instead of replayed.
   * </p>
   *
   * @param myGTNet      the local GTNet entry
   * @param previousRow  the row the first delivery created
   * @param originalCode the message code of the delivery being repeated
   * @return the envelope to answer the repeat with
   */
  public MessageEnvelope replayOutcome(GTNet myGTNet, GTNetMessage previousRow, byte originalCode) {
    List<GTNetMessage> answers = gtNetMessageJpaRepository.findByReplyTo(previousRow.getIdGtNetMessage());
    Optional<GTNetMessage> storedAnswer = answers.stream()
        .filter(answer -> answer.getSendRecv() == SendReceivedType.SEND).findFirst();

    if (storedAnswer.isPresent()) {
      // Returned as it stands, including its own errorMsgCode: the peer must see the same decision it was given the
      // first time, not a marker that overwrites the reason for it.
      return new MessageEnvelope(myGTNet, storedAnswer.get());
    }

    GTNetProtocolDescriptor originalDescriptor = messageCodeRegistry.getDescriptor(originalCode);
    boolean awaitingDecision = originalDescriptor != null && originalDescriptor.category() == MessageCategory.REQUEST;
    GTNetMessageCode answerCode = awaitingDecision ? GNetCoreMessageCode.GT_NET_DEFERRED_S
        : GNetCoreMessageCode.GT_NET_ACK_S;
    GTNetMessage answer = new GTNetMessage(previousRow.getIdGtNet(), LocalDateTime.now(ZoneOffset.UTC),
        SendReceivedType.ANSWER.getValue(), null, answerCode.getValue(),
        awaitingDecision ? "Already received, still awaiting a decision" : "Already received", null);
    answer.setErrorMsgCode(DUPLICATE_DELIVERY);
    return new MessageEnvelope(myGTNet, answer);
  }

  /**
   * Whether this code prefers to run again over being replayed from the store.
   *
   * @param messageCode the code of the repeated delivery
   * @return true when the protocol declares the code a side-effect-free query
   * @see GTNetProtocolDescriptor#reprocessable()
   */
  public boolean prefersReprocessing(byte messageCode) {
    GTNetProtocolDescriptor descriptor = messageCodeRegistry.getDescriptor(messageCode);
    return descriptor != null && descriptor.reprocessable();
  }
}
