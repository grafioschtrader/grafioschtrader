package grafiosch.gtnet.handler;

import java.util.Set;

import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.GTNetMessageCode;
import grafiosch.gtnet.MessageCategory;
import grafiosch.gtnet.m2m.model.MessageEnvelope;

/**
 * Strategy interface for handling GTNet messages.
 *
 * Each implementation handles one or more message code types. Implementations are auto-discovered by Spring and
 * registered in the handler registry.
 *
 * <h3>Implementation Guidelines</h3>
 * <ul>
 *   <li>Annotate implementations with {@code @Component} for Spring auto-discovery</li>
 *   <li>Extend {@link AbstractGTNetMessageHandler} for common functionality</li>
 *   <li>For request handlers, extend {@code AbstractRequestHandler} which integrates auto-response logic</li>
 *   <li>For announcement handlers, extend {@code AbstractAnnouncementHandler}</li>
 *   <li>Override {@link #getSupportedMessageCodes()} to handle multiple related message codes in one handler</li>
 * </ul>
 *
 * @see GTNetMessageHandlerRegistry for handler lookup
 * @see HandlerResult for possible return types
 */
public interface GTNetMessageHandler {

  /**
   * Handles the incoming message and returns a result indicating the outcome.
   *
   * @param context the message context containing request data and utilities
   * @return the handler result indicating response type or error
   * @throws Exception if processing fails unexpectedly
   */
  HandlerResult<GTNetMessage, MessageEnvelope> handle(GTNetMessageContext context) throws Exception;

  /**
   * Returns the primary message code this handler is responsible for.
   *
   * For handlers supporting multiple codes, this returns the first/primary code. Override
   * {@link #getSupportedMessageCodes()} to register all supported codes.
   */
  GTNetMessageCode getSupportedMessageCode();

  /**
   * Returns all message codes this handler can process.
   *
   * Override this method to handle multiple related message codes in a single handler. The default implementation
   * returns only the single code from {@link #getSupportedMessageCode()}.
   *
   * @return set of supported message codes
   */
  default Set<? extends GTNetMessageCode> getSupportedMessageCodes() {
    return Set.of(getSupportedMessageCode());
  }

  /**
   * Returns the category of messages this handler processes.
   *
   * Used for validation and to determine expected response behavior.
   */
  MessageCategory getCategory();
}
