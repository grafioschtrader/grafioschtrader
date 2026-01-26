package grafiosch.gtnet;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Registry for GTNet message codes that supports both core and application-specific codes.
 *
 * This registry provides a unified lookup mechanism for message codes, allowing both the
 * core library codes ({@link GNetCoreMessageCode}) and application-specific codes to be
 * registered and retrieved by their byte value.
 *
 * <h3>Usage</h3>
 * The core message codes are automatically registered during construction. Application-specific
 * codes should be registered at startup via {@link #registerMessageCode(GTNetMessageCode)}.
 *
 * <h3>Response Mapping</h3>
 * The registry also maintains a mapping from request codes to their valid response codes.
 * This is used by the UI to show available response options for unanswered incoming requests.
 */
@Component
public class GTNetMessageCodeRegistry {

  private static final Logger log = LoggerFactory.getLogger(GTNetMessageCodeRegistry.class);

  private final Map<Byte, GTNetMessageCode> codesByValue = new ConcurrentHashMap<>();
  private final Map<GTNetMessageCode, List<GTNetMessageCode>> responseMap = new ConcurrentHashMap<>();

  /**
   * Creates the registry and registers all core message codes.
   */
  public GTNetMessageCodeRegistry() {
    // Register all core message codes
    for (GNetCoreMessageCode code : GNetCoreMessageCode.values()) {
      registerMessageCode(code);
    }

    // Register core response mappings
    registerResponseMapping(GNetCoreMessageCode.GT_NET_FIRST_HANDSHAKE_SEL_RR_S,
        GNetCoreMessageCode.GT_NET_FIRST_HANDSHAKE_ACCEPT_S,
        GNetCoreMessageCode.GT_NET_FIRST_HANDSHAKE_REJECT_S);

    registerResponseMapping(GNetCoreMessageCode.GT_NET_UPDATE_SERVERLIST_SEL_RR_C,
        GNetCoreMessageCode.GT_NET_UPDATE_SERVERLIST_ACCEPT_S,
        GNetCoreMessageCode.GT_NET_UPDATE_SERVERLIST_REJECTED_S);

    registerResponseMapping(GNetCoreMessageCode.GT_NET_DATA_REQUEST_SEL_RR_C,
        GNetCoreMessageCode.GT_NET_DATA_REQUEST_ACCEPT_S,
        GNetCoreMessageCode.GT_NET_DATA_REQUEST_REJECTED_S);

    log.info("GTNet message code registry initialized with {} core codes", GNetCoreMessageCode.values().length);
  }

  /**
   * Registers a message code in the registry.
   *
   * @param code the message code to register
   * @throws IllegalStateException if a code with the same value is already registered
   */
  public void registerMessageCode(GTNetMessageCode code) {
    GTNetMessageCode existing = codesByValue.putIfAbsent(code.getValue(), code);
    if (existing != null && existing != code) {
      throw new IllegalStateException(String.format(
          "Duplicate message code value %d: existing=%s, new=%s",
          code.getValue(), existing.name(), code.name()));
    }
    log.debug("Registered message code {} with value {}", code.name(), code.getValue());
  }

  /**
   * Registers a request-to-response mapping.
   *
   * @param requestCode the request code (must be an _RR_ type)
   * @param responseCodes the valid response codes for this request
   */
  public void registerResponseMapping(GTNetMessageCode requestCode, GTNetMessageCode... responseCodes) {
    responseMap.put(requestCode, List.of(responseCodes));
    log.debug("Registered response mapping for {} with {} responses", requestCode.name(), responseCodes.length);
  }

  /**
   * Looks up a message code by its byte value.
   *
   * @param value the byte value to look up
   * @return the corresponding GTNetMessageCode, or null if not found
   */
  public GTNetMessageCode getByValue(byte value) {
    return codesByValue.get(value);
  }

  /**
   * Returns the valid response codes for a given request code.
   *
   * @param requestCode the request message code (must be an _RR_ type)
   * @return list of valid response codes, or empty list if not a request type
   */
  public List<GTNetMessageCode> getValidResponses(GTNetMessageCode requestCode) {
    return responseMap.getOrDefault(requestCode, Collections.emptyList());
  }

  /**
   * Checks if a message code with the given value is registered.
   *
   * @param value the byte value to check
   * @return true if a code with this value is registered
   */
  public boolean hasCode(byte value) {
    return codesByValue.containsKey(value);
  }

  /**
   * Returns all registered message codes.
   *
   * @return unmodifiable collection of all registered codes
   */
  public java.util.Collection<GTNetMessageCode> getAllCodes() {
    return Collections.unmodifiableCollection(codesByValue.values());
  }

  /**
   * Returns all registered response mappings.
   *
   * @return unmodifiable map of request codes to their response codes
   */
  public Map<GTNetMessageCode, List<GTNetMessageCode>> getAllResponseMappings() {
    return Collections.unmodifiableMap(new HashMap<>(responseMap));
  }
}
