package grafiosch.gtnet;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Registry mapping GTNet message codes to their payload model classes and metadata.
 *
 * This registry provides a unified mechanism for both core library message models and
 * application-specific message models. Registration is done via Spring bean initialization.
 *
 * <h3>Usage</h3>
 * <pre>
 * // In Spring configuration or @PostConstruct
 * modelRegistry.registerModel(GNetCoreMessageCode.GT_NET_MAINTENANCE_ALL_C, MaintenanceMsg.class, false, (byte) 10);
 * </pre>
 *
 * @see GTNetMessageCode for the message code interface
 */
@Component
public class GTNetModelRegistry {

  private static final Logger log = LoggerFactory.getLogger(GTNetModelRegistry.class);

  /** Pattern in message code name indicating broadcast to all applicable domains. */
  public static final String MESSAGE_TO_ALL = "_ALL_";

  /** Pattern in message code name indicating client-initiated (UI-triggerable) messages. */
  public static final String IS_USER_REQUEST_STR = "_C";

  /** Registry mapping message codes to their model class and response expectation. */
  private final Map<GTNetMessageCode, GTNetMsgRequest> msgFormMap = new ConcurrentHashMap<>();

  /**
   * Creates the registry. Core message models are NOT registered here - they should be
   * registered via a configuration class that has access to the model classes.
   */
  public GTNetModelRegistry() {
    log.info("GTNet model registry initialized");
  }

  /**
   * Registers a message model.
   *
   * @param messageCode the message code
   * @param model the model class (can be null for messages with no payload)
   * @param responseExpected true if sender should wait for a response
   * @param repeatSendAsMany retry count for delivery attempts
   */
  public void registerModel(GTNetMessageCode messageCode, Class<?> model, boolean responseExpected, byte repeatSendAsMany) {
    GTNetMsgRequest request = new GTNetMsgRequest(model, responseExpected, repeatSendAsMany);
    msgFormMap.put(messageCode, request);
    log.debug("Registered message model for {}: model={}, responseExpected={}",
        messageCode.name(), model != null ? model.getSimpleName() : "null", responseExpected);
  }

  /**
   * Registers a message model with default retry count of 1.
   *
   * @param messageCode the message code
   * @param model the model class (can be null for messages with no payload)
   * @param responseExpected true if sender should wait for a response
   */
  public void registerModel(GTNetMessageCode messageCode, Class<?> model, boolean responseExpected) {
    registerModel(messageCode, model, responseExpected, (byte) 1);
  }

  /**
   * Looks up the model class and response expectation for a given message code.
   *
   * @param messageCode the message code to look up
   * @return the GTNetMsgRequest with model class and response flag, or null if not registered
   */
  public GTNetMsgRequest getModel(GTNetMessageCode messageCode) {
    return msgFormMap.get(messageCode);
  }

  /**
   * Looks up a model by message code byte value.
   *
   * @param codeValue the byte value of the message code
   * @return the GTNetMsgRequest, or null if not found
   */
  public GTNetMsgRequest getModelByValue(byte codeValue) {
    return msgFormMap.entrySet().stream()
        .filter(e -> e.getKey().getValue() == codeValue)
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(null);
  }

  /**
   * Returns all registered message codes.
   *
   * @return unmodifiable map of all registrations
   */
  public Map<GTNetMessageCode, GTNetMsgRequest> getAllModels() {
    return Collections.unmodifiableMap(msgFormMap);
  }

  /**
   * Returns only client-initiatable message codes with non-null models.
   *
   * @return map of message codes ending with "_C" that have model classes
   */
  public Map<GTNetMessageCode, GTNetMsgRequest> getClientInitiatableModels() {
    return msgFormMap.entrySet().stream()
        .filter(e -> e.getKey().name().endsWith(IS_USER_REQUEST_STR) && e.getValue().model != null)
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  /**
   * Checks if a message code is registered.
   *
   * @param messageCode the message code to check
   * @return true if registered
   */
  public boolean hasModel(GTNetMessageCode messageCode) {
    return msgFormMap.containsKey(messageCode);
  }

  /**
   * Metadata container for a registered message type.
   */
  public static class GTNetMsgRequest {
    /** The POJO class representing the message payload structure. Null for messages with no parameters. */
    public Class<?> model;

    /** True if the sender should wait for a synchronous response after sending this message type. */
    public boolean responseExpected;

    /** Certain messages should be sent repeatedly until received or until the limit for transmission attempts has been reached. */
    public byte repeatSendAsMany;

    public GTNetMsgRequest(Class<?> model, boolean responseExpected, byte repeatSendAsMany) {
      this.model = model;
      this.responseExpected = responseExpected;
      this.repeatSendAsMany = repeatSendAsMany;
    }
  }
}
