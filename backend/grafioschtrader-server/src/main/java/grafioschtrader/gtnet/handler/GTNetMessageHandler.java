package grafioschtrader.gtnet.handler;

import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.MessageCategory;

/**
 * Strategy interface for handling GTNet messages.
 *
 * Each implementation handles one specific message code type. Implementations are auto-discovered by Spring and
 * registered in {@link GTNetMessageHandlerRegistry}.
 *
 * <h3>Implementation Guidelines</h3>
 * <ul>
 *   <li>Annotate implementations with {@code @Component} for Spring auto-discovery</li>
 *   <li>Extend {@link AbstractGTNetMessageHandler} for common functionality</li>
 *   <li>For request handlers, extend {@link AbstractRequestHandler} which integrates auto-response logic</li>
 *   <li>For announcement handlers, extend {@link AbstractAnnouncementHandler}</li>
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
  HandlerResult handle(GTNetMessageContext context) throws Exception;

  /**
   * Returns the message code this handler is responsible for.
   *
   * Each handler handles exactly one message code. The registry uses this to route incoming messages.
   */
  GTNetMessageCodeType getSupportedMessageCode();

  /**
   * Returns the category of messages this handler processes.
   *
   * Used for validation and to determine expected response behavior.
   */
  MessageCategory getCategory();
}
