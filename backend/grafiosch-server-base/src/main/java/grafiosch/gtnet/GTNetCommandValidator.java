package grafiosch.gtnet;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafiosch.entities.GTNetMessage;
import grafiosch.exceptions.DataViolationException;
import grafiosch.gtnet.model.MsgRequest;
import grafiosch.repository.GTNetMessageJpaRepository;

/**
 * Checks a message an administrator submits from the UI before anything is written or sent.
 *
 * <p>
 * {@code MsgRequest} is a plain DTO whose {@code @Valid} has nothing to validate, and the code it names decides which
 * path {@code submitMsg} takes. Without a check here a response that answers no request still runs the side effects of
 * an answer — a submitted {@code GT_NET_DATA_REQUEST_ACCEPT_S} without a {@code replyTo} ends up granting every default
 * exchange kind in both directions.
 * </p>
 *
 * <p>
 * The rules below are about the shape of the command, not about the business outcome: whether the code may be sent by a
 * person at all, whether it names a target, and whether a response really answers an open request of that peer. Every
 * one of them is read from the protocol descriptor of the code.
 * </p>
 */
@Component
public class GTNetCommandValidator {

  /** The field key of the rejection, dot-separated lower case so the UI resolves it as a translated label. */
  private static final String FIELD_MESSAGE_CODE = "message.code";

  /** The field key of every rejection that is about what the command answers. */
  private static final String FIELD_REPLY_TO = "reply.to";

  @Autowired
  private GTNetMessageCodeRegistry messageCodeRegistry;

  @Autowired
  private GTNetMessageJpaRepository gtNetMessageJpaRepository;

  /**
   * Validates a submitted message. Throws rather than returning a verdict, because every failure is a bug in the caller
   * or a stale UI state and none of them has a partial outcome worth continuing with.
   *
   * @param msgRequest  the submitted command
   * @param messageCode the already resolved code of that command
   * @throws DataViolationException when the command may not be sent in this form
   */
  public void validate(MsgRequest msgRequest, GTNetMessageCode messageCode) {
    GTNetProtocolDescriptor descriptor = messageCodeRegistry.getDescriptor(messageCode.getValue());
    if (descriptor == null) {
      throw new DataViolationException(FIELD_MESSAGE_CODE, "gt.gtnet.invalid.message.code",
          new Object[] { messageCode.name() });
    }
    if (!descriptor.userInitiable()) {
      // Codes the peers exchange among themselves - price queries, sync, acknowledgements - are not commands.
      throw new DataViolationException(FIELD_MESSAGE_CODE, "gt.gtnet.command.not.user.initiable",
          new Object[] { messageCode.name() });
    }

    boolean isResponse = descriptor.category() == MessageCategory.RESPONSE;
    if (isResponse) {
      validateResponse(msgRequest, descriptor);
    } else if (msgRequest.replyTo != null) {
      if (!descriptor.threadable()) {
        // A request or an announcement answers nothing, so a replyTo on it would build a thread that has no reply.
        throw new DataViolationException(FIELD_REPLY_TO, "gt.gtnet.command.reply.to.not.allowed",
            new Object[] { messageCode.name() });
      }
      validateThreadedReply(msgRequest, descriptor);
    }

    if (msgRequest.waitDaysApply != null && msgRequest.waitDaysApply > 0 && !isResponse) {
      // The cooling-off period is what a refusal imposes on the requester; it is meaningless on anything else.
      throw new DataViolationException("wait.days.apply", "gt.gtnet.command.wait.days.only.on.response",
          new Object[] { messageCode.name() });
    }
  }

  /**
   * A response must answer a request that this peer really sent us and that is still open, and its code must be one of
   * the answers registered for that request.
   *
   * @param msgRequest the submitted command
   * @param descriptor the protocol descriptor of that command
   */
  private void validateResponse(MsgRequest msgRequest, GTNetProtocolDescriptor descriptor) {
    GTNetMessage answeredRequest = requireAnsweredMessage(msgRequest, descriptor);
    if (!gtNetMessageJpaRepository.findByReplyTo(answeredRequest.getIdGtNetMessage()).isEmpty()) {
      throw new DataViolationException(FIELD_REPLY_TO, "gt.gtnet.command.reply.to.already.answered", null);
    }
    if (!messageCodeRegistry.isValidResponse(answeredRequest.getMessageCodeValue(), descriptor.value())) {
      throw new DataViolationException(FIELD_MESSAGE_CODE, "gt.gtnet.command.not.a.valid.response",
          new Object[] { descriptor.name() });
    }
  }

  /**
   * A threadable code — the admin message is the only one — continues a conversation instead of closing a request. It
   * still has to continue one that exists, that came from the peer being addressed, and that accepts this code as its
   * continuation. Several messages may hang off the same one, because a conversation is not a request with one answer.
   *
   * @param msgRequest the submitted command
   * @param descriptor the protocol descriptor of that command
   */
  private void validateThreadedReply(MsgRequest msgRequest, GTNetProtocolDescriptor descriptor) {
    GTNetMessage parent = requireAnsweredMessage(msgRequest, descriptor);
    if (!messageCodeRegistry.isValidResponse(parent.getMessageCodeValue(), descriptor.value())) {
      throw new DataViolationException(FIELD_MESSAGE_CODE, "gt.gtnet.command.not.a.valid.response",
          new Object[] { descriptor.name() });
    }
  }

  /**
   * The message a command claims to answer, which must exist, must have been received rather than sent, and must belong
   * to the peer the command is aimed at.
   *
   * @param msgRequest the submitted command
   * @param descriptor the protocol descriptor of that command
   * @return the referenced message
   */
  private GTNetMessage requireAnsweredMessage(MsgRequest msgRequest, GTNetProtocolDescriptor descriptor) {
    if (msgRequest.replyTo == null) {
      throw new DataViolationException(FIELD_REPLY_TO, "gt.gtnet.command.reply.to.required",
          new Object[] { descriptor.name() });
    }
    GTNetMessage referenced = gtNetMessageJpaRepository.findByIdGtNetMessage(msgRequest.replyTo);
    if (referenced == null || referenced.getSendRecv() != SendReceivedType.RECEIVED) {
      throw new DataViolationException(FIELD_REPLY_TO, "gt.gtnet.command.reply.to.not.received", null);
    }
    if (msgRequest.idGTNetTargetDomain != null && !msgRequest.idGTNetTargetDomain.equals(referenced.getIdGtNet())) {
      throw new DataViolationException(FIELD_REPLY_TO, "gt.gtnet.command.reply.to.other.peer", null);
    }
    return referenced;
  }

  /**
   * The request a response answers, when the submitted command is a well-formed response.
   *
   * @param msgRequest the submitted command
   * @return the answered request, empty when the command is not a response
   */
  public Optional<GTNetMessage> findAnsweredRequest(MsgRequest msgRequest) {
    return msgRequest.replyTo == null ? Optional.empty()
        : Optional.ofNullable(gtNetMessageJpaRepository.findByIdGtNetMessage(msgRequest.replyTo));
  }
}
