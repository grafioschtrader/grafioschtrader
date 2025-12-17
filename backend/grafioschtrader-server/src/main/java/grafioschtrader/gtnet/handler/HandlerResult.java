package grafioschtrader.gtnet.handler;

import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.gtnet.m2m.model.MessageEnvelope;

/**
 * Sealed interface representing the result of GTNet message processing.
 *
 * Each handler returns one of four possible outcomes:
 * <ul>
 *   <li>{@link ImmediateResponse}: Send response immediately to the caller</li>
 *   <li>{@link AwaitingManualResponse}: Message stored, awaiting admin action</li>
 *   <li>{@link NoResponseNeeded}: Processing complete, no reply required</li>
 *   <li>{@link ProcessingError}: Processing failed with error details</li>
 * </ul>
 *
 * @see GTNetMessageHandler for the handler interface
 */
public sealed interface HandlerResult
    permits HandlerResult.ImmediateResponse, HandlerResult.AwaitingManualResponse, HandlerResult.NoResponseNeeded,
    HandlerResult.ProcessingError {

  /**
   * Result indicating an immediate response should be sent back to the caller.
   *
   * Used when:
   * <ul>
   *   <li>GTNetMessageAnswer auto-response rules matched and produced a response</li>
   *   <li>The message type always requires immediate response (e.g., PING)</li>
   *   <li>First handshake that can be auto-accepted</li>
   * </ul>
   *
   * @param response the MessageEnvelope to send back to the remote server
   */
  record ImmediateResponse(MessageEnvelope response) implements HandlerResult {
  }

  /**
   * Result indicating the message was stored and awaits manual admin action.
   *
   * Used when a request message was received but no GTNetMessageAnswer auto-response rule matched. The admin must
   * review the request in the UI and manually send an accept/reject response.
   *
   * @param storedMessage the persisted GTNetMessage awaiting review
   */
  record AwaitingManualResponse(GTNetMessage storedMessage) implements HandlerResult {
  }

  /**
   * Result indicating processing completed successfully with no response needed.
   *
   * Used for:
   * <ul>
   *   <li>Announcement messages (maintenance, discontinuation, revoke)</li>
   *   <li>Response messages (accept/reject replies to our earlier requests)</li>
   * </ul>
   */
  record NoResponseNeeded() implements HandlerResult {
  }

  /**
   * Result indicating message processing failed with an error.
   *
   * The error information is used to build an error response to the caller.
   *
   * @param errorCode i18n error code for translation
   * @param message   human-readable error description
   */
  record ProcessingError(String errorCode, String message) implements HandlerResult {
  }
}
