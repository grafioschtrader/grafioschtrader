package grafiosch.m2m.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.GTNetTime;
import grafiosch.gtnet.m2m.model.MessageEnvelope;

/**
 * Turns the failures that happen before {@link GTNetM2MResource#receiveMessage} is entered into the same protocol
 * answer every other refusal uses.
 *
 * <p>
 * A body that Jackson cannot bind — malformed JSON, a field of the wrong type, a truncated request — never reaches the
 * controller method, so the envelope validator inside {@code getMsgResponse} cannot see it either. Left to the global
 * error handler it would become an HTTP 400, and {@code BaseDataClient} maps every non-2xx to a result whose response
 * envelope is null: the sending peer would record a failed delivery with no reason. Answering HTTP 200 with
 * {@link GNetCoreMessageCode#GT_NET_ERROR_S} and {@code ENVELOPE_INVALID} keeps the reason readable.
 * </p>
 *
 * <p>
 * Scoped to the one M2M controller so that ordinary {@code /api/**} error handling is untouched. The answer is built by
 * hand rather than through the repository, because an error path must not need a database round trip; a null
 * {@code sourceGtNet} is tolerated by the receiving side.
 * </p>
 */
@RestControllerAdvice(assignableTypes = GTNetM2MResource.class)
public class GTNetM2MExceptionAdvice {

  /**
   * Answers an unreadable request body as a protocol rejection.
   *
   * @param ex the binding failure
   * @return an envelope carrying {@code ENVELOPE_INVALID}, always with HTTP 200
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<MessageEnvelope> onUnreadableBody(HttpMessageNotReadableException ex) {
    boolean tooLarge = hasBodyTooLargeCause(ex);
    MessageEnvelope error = new MessageEnvelope();
    error.timestamp = GTNetTime.now();
    error.messageCode = GNetCoreMessageCode.GT_NET_ERROR_S.getValue();
    error.errorMsgCode = tooLarge ? "PAYLOAD_TOO_LARGE" : "ENVELOPE_INVALID";
    error.message = tooLarge ? "The message body exceeds the accepted size" : "The message body could not be read";
    return new ResponseEntity<>(error, HttpStatus.OK);
  }

  /**
   * An aborted read of an over-sized chunked body surfaces as an unreadable body, so the real reason has to be
   * recovered from the cause chain to keep the two rejections distinguishable for the sender.
   *
   * @param ex the binding failure
   * @return true when the body was aborted by the size cap
   */
  private static boolean hasBodyTooLargeCause(Throwable ex) {
    for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
      if (cause instanceof GTNetBodyTooLargeException) {
        return true;
      }
      if (cause.getCause() == cause) {
        break;
      }
    }
    return false;
  }
}
