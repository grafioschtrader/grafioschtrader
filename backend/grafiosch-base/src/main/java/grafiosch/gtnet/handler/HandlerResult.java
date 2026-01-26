package grafiosch.gtnet.handler;

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
 * @param <M> the message type
 * @param <E> the envelope type
 */
public sealed interface HandlerResult<M, E>
    permits HandlerResult.ImmediateResponse, HandlerResult.AwaitingManualResponse, HandlerResult.NoResponseNeeded,
    HandlerResult.ProcessingError {

  /**
   * Result indicating an immediate response should be sent back to the caller.
   *
   * Used when:
   * <ul>
   *   <li>Auto-response rules matched and produced a response</li>
   *   <li>The message type always requires immediate response (e.g., PING)</li>
   *   <li>First handshake that can be auto-accepted</li>
   * </ul>
   *
   * @param response the envelope to send back to the remote server
   * @param <M> the message type
   * @param <E> the envelope type
   */
  record ImmediateResponse<M, E>(E response) implements HandlerResult<M, E> {
  }

  /**
   * Result indicating the message was stored and awaits manual admin action.
   *
   * Used when a request message was received but no auto-response rule matched. The admin must
   * review the request in the UI and manually send an accept/reject response.
   *
   * @param storedMessage the persisted message awaiting review
   * @param <M> the message type
   * @param <E> the envelope type
   */
  record AwaitingManualResponse<M, E>(M storedMessage) implements HandlerResult<M, E> {
  }

  /**
   * Result indicating processing completed successfully with no response needed.
   *
   * Used for:
   * <ul>
   *   <li>Announcement messages (maintenance, discontinuation, revoke)</li>
   *   <li>Response messages (accept/reject replies to our earlier requests)</li>
   * </ul>
   *
   * @param <M> the message type
   * @param <E> the envelope type
   */
  record NoResponseNeeded<M, E>() implements HandlerResult<M, E> {
  }

  /**
   * Result indicating message processing failed with an error.
   *
   * The error information is used to build an error response to the caller.
   *
   * @param errorCode i18n error code for translation
   * @param message   human-readable error description
   * @param <M> the message type
   * @param <E> the envelope type
   */
  record ProcessingError<M, E>(String errorCode, String message) implements HandlerResult<M, E> {
  }
}
