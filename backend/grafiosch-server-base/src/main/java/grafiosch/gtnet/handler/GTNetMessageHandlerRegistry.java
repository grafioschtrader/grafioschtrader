package grafiosch.gtnet.handler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import grafiosch.gtnet.GTNetMessageCode;
import grafiosch.gtnet.MessageCategory;

/**
 * Registry that maps GTNet message codes to their handlers.
 *
 * Handlers are auto-discovered via Spring dependency injection. All beans implementing {@link GTNetMessageHandler} are
 * automatically registered during construction.
 *
 * <h3>Usage</h3>
 *
 * <pre>
 * GTNetMessageHandler handler = registry.getHandler(messageCodeValue);
 * HandlerResult result = handler.handle(context);
 * </pre>
 *
 * @see GTNetMessageHandler for the handler interface
 */
@Component
public class GTNetMessageHandlerRegistry {

  private static final Logger log = LoggerFactory.getLogger(GTNetMessageHandlerRegistry.class);

  private final Map<Byte, GTNetMessageHandler> handlers = new HashMap<>();
  private final Map<Byte, GTNetMessageCode> messageCodes = new HashMap<>();

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
      for (GTNetMessageCode code : handler.getSupportedMessageCodes()) {
        byte codeValue = code.getValue();
        if (handlers.containsKey(codeValue)) {
          throw new IllegalStateException(String.format("Duplicate handler for message code %s: existing=%s, new=%s",
              code.name(), handlers.get(codeValue).getClass().getSimpleName(), handler.getClass().getSimpleName()));
        }
        handlers.put(codeValue, handler);
        messageCodes.put(codeValue, code);
        log.debug("Registered handler {} for message code {}", handler.getClass().getSimpleName(), code.name());
      }
    }
    log.info("GTNet message handler registry initialized with {} handlers for {} message codes", handlerList.size(),
        handlers.size());
  }

  /**
   * Returns the handler for the given message code value.
   *
   * @param codeValue the message code byte value to look up
   * @return the registered handler
   * @throws IllegalArgumentException if no handler is registered for the code
   */
  public GTNetMessageHandler getHandler(byte codeValue) {
    GTNetMessageHandler handler = handlers.get(codeValue);
    if (handler == null) {
      throw new IllegalArgumentException("No handler registered for message code: " + codeValue);
    }
    return handler;
  }

  /**
   * Returns the handler for the given message code, if registered.
   *
   * @param codeValue the message code byte value to look up
   * @return Optional containing the handler, or empty if not registered
   */
  public Optional<GTNetMessageHandler> findHandler(byte codeValue) {
    return Optional.ofNullable(handlers.get(codeValue));
  }

  /**
   * Checks if a handler is registered for the given message code.
   *
   * @param codeValue the message code byte value to check
   * @return true if a handler is registered
   */
  public boolean hasHandler(byte codeValue) {
    return handlers.containsKey(codeValue);
  }

  /**
   * Returns all registered message code values.
   *
   * @return set of message code values with registered handlers
   */
  public Set<Byte> getRegisteredCodeValues() {
    return handlers.keySet();
  }

  /**
   * Returns the GTNetMessageCode for a given byte value.
   *
   * @param codeValue the byte value
   * @return the message code, or null if not registered
   */
  public GTNetMessageCode getMessageCode(byte codeValue) {
    return messageCodes.get(codeValue);
  }

  /**
   * Returns the category of the handler for the given message code.
   *
   * @param codeValue the message code byte value to look up
   * @return the message category, or null if no handler is registered
   */
  public MessageCategory getCategory(byte codeValue) {
    GTNetMessageHandler handler = handlers.get(codeValue);
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
