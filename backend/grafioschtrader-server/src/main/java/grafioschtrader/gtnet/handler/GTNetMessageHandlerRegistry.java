package grafioschtrader.gtnet.handler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.MessageCategory;

/**
 * Registry that maps GTNet message codes to their handlers.
 *
 * Handlers are auto-discovered via Spring dependency injection. All beans implementing {@link GTNetMessageHandler} are
 * automatically registered during construction.
 *
 * <h3>Usage</h3>
 *
 * <pre>
 * GTNetMessageHandler handler = registry.getHandler(messageCode);
 * HandlerResult result = handler.handle(context);
 * </pre>
 *
 * @see GTNetMessageHandler for the handler interface
 */
@Component
public class GTNetMessageHandlerRegistry {

  private static final Logger log = LoggerFactory.getLogger(GTNetMessageHandlerRegistry.class);

  private final Map<GTNetMessageCodeType, GTNetMessageHandler> handlers = new HashMap<>();

  /**
   * Creates the registry and registers all discovered handlers.
   *
   * Spring automatically injects all beans implementing GTNetMessageHandler. Each handler can support multiple message
   * codes via {@link GTNetMessageHandler#getSupportedMessageCodes()}.
   *
   * @param handlerList all GTNetMessageHandler beans in the application context
   * @throws IllegalStateException if duplicate handlers are found for the same message code
   */
  public GTNetMessageHandlerRegistry(List<GTNetMessageHandler> handlerList) {
    for (GTNetMessageHandler handler : handlerList) {
      for (GTNetMessageCodeType code : handler.getSupportedMessageCodes()) {
        if (handlers.containsKey(code)) {
          throw new IllegalStateException(String.format("Duplicate handler for message code %s: existing=%s, new=%s",
              code, handlers.get(code).getClass().getSimpleName(), handler.getClass().getSimpleName()));
        }
        handlers.put(code, handler);
        log.debug("Registered handler {} for message code {}", handler.getClass().getSimpleName(), code);
      }
    }
    log.info("GTNet message handler registry initialized with {} handlers for {} message codes", handlerList.size(),
        handlers.size());
  }

  /**
   * Returns the handler for the given message code.
   *
   * @param messageCode the message code to look up
   * @return the registered handler
   * @throws IllegalArgumentException if no handler is registered for the code
   */
  public GTNetMessageHandler getHandler(GTNetMessageCodeType messageCode) {
    GTNetMessageHandler handler = handlers.get(messageCode);
    if (handler == null) {
      throw new IllegalArgumentException("No handler registered for message code: " + messageCode);
    }
    return handler;
  }

  /**
   * Returns the handler for the given message code, if registered.
   *
   * @param messageCode the message code to look up
   * @return Optional containing the handler, or empty if not registered
   */
  public Optional<GTNetMessageHandler> findHandler(GTNetMessageCodeType messageCode) {
    return Optional.ofNullable(handlers.get(messageCode));
  }

  /**
   * Checks if a handler is registered for the given message code.
   *
   * @param messageCode the message code to check
   * @return true if a handler is registered
   */
  public boolean hasHandler(GTNetMessageCodeType messageCode) {
    return handlers.containsKey(messageCode);
  }

  /**
   * Returns all registered message codes.
   *
   * @return set of message codes with registered handlers
   */
  public Set<GTNetMessageCodeType> getRegisteredCodes() {
    return handlers.keySet();
  }

  /**
   * Returns the category of the handler for the given message code.
   *
   * @param messageCode the message code to look up
   * @return the message category, or null if no handler is registered
   */
  public MessageCategory getCategory(GTNetMessageCodeType messageCode) {
    GTNetMessageHandler handler = handlers.get(messageCode);
    return handler != null ? handler.getCategory() : null;
  }

  /**
   * Returns the number of registered handlers.
   *
   * @return handler count
   */
  public int getHandlerCount() {
    return handlers.size();
  }
}
