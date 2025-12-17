package grafioschtrader.gtnet.handler;

import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;

import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.entities.GTNetMessage.GTNetMessageParam;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.MessageCategory;
import grafioschtrader.gtnet.handler.GTNetResponseResolver.ResolvedResponse;
import grafioschtrader.gtnet.m2m.model.MessageEnvelope;

/**
 * Abstract base class for handlers that process incoming request messages.
 *
 * Request messages expect a response, which may be:
 * <ul>
 *   <li>Immediate: Generated automatically via GTNetMessageAnswer rules</li>
 *   <li>Deferred: Stored for manual admin review and later response</li>
 * </ul>
 *
 * This class implements the Template Method pattern, providing a consistent request handling flow:
 * <ol>
 *   <li>Validate the request</li>
 *   <li>Store the incoming message</li>
 *   <li>Process request-specific side effects</li>
 *   <li>Attempt automatic response via GTNetMessageAnswer rules</li>
 *   <li>Return immediate response or await manual handling</li>
 * </ol>
 *
 * Subclasses implement the abstract methods to provide request-specific behavior.
 */
public abstract class AbstractRequestHandler extends AbstractGTNetMessageHandler {

  @Autowired
  protected GTNetResponseResolver responseResolver;

  @Override
  public final MessageCategory getCategory() {
    return MessageCategory.REQUEST;
  }

  @Override
  public final HandlerResult handle(GTNetMessageContext context) throws Exception {
    // 1. Validate the request
    ValidationResult validation = validateRequest(context);
    if (!validation.valid()) {
      return new HandlerResult.ProcessingError(validation.errorCode(), validation.message());
    }

    // 2. Store the incoming message
    GTNetMessage storedRequest = storeIncomingMessage(context);

    // 3. Process request-specific side effects (e.g., store remote GTNet for handshake)
    processRequestSideEffects(context, storedRequest);

    // 4. Try automatic response via GTNetMessageAnswer rules
    Optional<ResolvedResponse> autoResponse = responseResolver.resolveAutoResponse(context.getAutoResponseRules(),
        context.getRemoteGTNet(), context.getParams());

    if (autoResponse.isPresent()) {
      // 5a. Build and return automatic response
      ResolvedResponse resolved = autoResponse.get();

      // Apply any post-response actions (e.g., update GTNet state after accept)
      applyResponseSideEffects(context, resolved.responseCode(), storedRequest);

      MessageEnvelope response = buildResponse(context, resolved.responseCode(), resolved.message(), storedRequest);
      return new HandlerResult.ImmediateResponse(response);
    } else {
      // 5b. No auto-response rule matched, wait for admin
      return new HandlerResult.AwaitingManualResponse(storedRequest);
    }
  }

  /**
   * Validates the incoming request.
   *
   * Subclasses should check:
   * <ul>
   *   <li>Required parameters are present</li>
   *   <li>Parameter values are valid</li>
   *   <li>Pre-conditions are met (e.g., remote domain is known)</li>
   * </ul>
   *
   * @param context the message context
   * @return validation result indicating success or failure with error details
   */
  protected abstract ValidationResult validateRequest(GTNetMessageContext context);

  /**
   * Processes request-specific side effects before response determination.
   *
   * Called after the message is stored but before auto-response is evaluated. Use for operations that must happen
   * regardless of the response, such as storing a new remote GTNet entity during first handshake.
   *
   * @param context       the message context
   * @param storedRequest the persisted request message
   */
  protected abstract void processRequestSideEffects(GTNetMessageContext context, GTNetMessage storedRequest);

  /**
   * Applies side effects after a response is determined.
   *
   * Called only when an automatic response is generated. Use for operations that depend on the response type, such as
   * updating GTNet state flags after accepting a request.
   *
   * Default implementation does nothing. Override if response-dependent side effects are needed.
   *
   * @param context       the message context
   * @param responseCode  the determined response code
   * @param storedRequest the original request message
   */
  protected void applyResponseSideEffects(GTNetMessageContext context, GTNetMessageCodeType responseCode,
      GTNetMessage storedRequest) {
    // Default: no side effects
  }

  /**
   * Builds the response envelope for the given response code.
   *
   * Default implementation creates a response message with the given code and message, stores it, and wraps it in an
   * envelope. Override to customize response building (e.g., add payload).
   *
   * @param context         the message context
   * @param responseCode    the response message code
   * @param message         optional text message
   * @param originalRequest the original request message (for reply linking)
   * @return the response envelope ready for transmission
   */
  protected MessageEnvelope buildResponse(GTNetMessageContext context, GTNetMessageCodeType responseCode, String message,
      GTNetMessage originalRequest) {
    Map<String, GTNetMessageParam> responseParams = buildResponseParams(context, responseCode);
    GTNetMessage responseMsg = storeResponseMessage(context, responseCode, message, responseParams, originalRequest);
    return createResponseEnvelope(context, responseMsg);
  }

  /**
   * Builds the response parameters map.
   *
   * Default implementation returns an empty map. Override to add response-specific parameters.
   *
   * @param context      the message context
   * @param responseCode the response message code
   * @return map of parameter names to values
   */
  protected Map<String, GTNetMessageParam> buildResponseParams(GTNetMessageContext context,
      GTNetMessageCodeType responseCode) {
    return new java.util.HashMap<>();
  }
}
