package grafioschtrader.gtnet.handler;

import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.gtnet.MessageCategory;

/**
 * Abstract base class for handlers that process response messages.
 *
 * Response messages are replies to our previous requests (e.g., accept/reject responses). They update local state based
 * on the remote server's decision but do not generate a reply.
 *
 * Examples:
 * <ul>
 *   <li>GT_NET_FIRST_HANDSHAKE_ACCEPT_S - Remote accepted our handshake</li>
 *   <li>GT_NET_DATA_REQUEST_REJECTED_S - Remote rejected our data request</li>
 *   <li>GT_NET_DATA_REQUEST_IN_PROCESS_S - Remote is processing our data request</li>
 * </ul>
 */
public abstract class AbstractResponseHandler extends AbstractGTNetMessageHandler {

  @Override
  public final MessageCategory getCategory() {
    return MessageCategory.RESPONSE;
  }

  @Override
  public final HandlerResult handle(GTNetMessageContext context) throws Exception {
    // 1. Validate the response
    ValidationResult validation = validateResponse(context);
    if (!validation.valid()) {
      return new HandlerResult.ProcessingError(validation.errorCode(), validation.message());
    }

    // 2. Store the response message
    GTNetMessage storedMessage = storeIncomingMessage(context);

    // 3. Process response-specific side effects (e.g., update GTNet state, store tokens)
    processResponseSideEffects(context, storedMessage);

    // 4. No reply to a response
    return new HandlerResult.NoResponseNeeded();
  }

  /**
   * Validates the response message.
   *
   * Default implementation always returns valid. Override to add validation if needed.
   *
   * @param context the message context
   * @return validation result
   */
  protected ValidationResult validateResponse(GTNetMessageContext context) {
    return ValidationResult.ok();
  }

  /**
   * Processes response-specific side effects.
   *
   * Called after the response is stored. Use for operations like:
   * <ul>
   *   <li>Updating GTNet entity flags based on accept/reject</li>
   *   <li>Storing tokens from handshake accept responses</li>
   *   <li>Updating server state based on response</li>
   * </ul>
   *
   * @param context       the message context
   * @param storedMessage the persisted response message
   */
  protected abstract void processResponseSideEffects(GTNetMessageContext context, GTNetMessage storedMessage);
}
