package grafiosch.gtnet;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The one authority on the GTNet protocol: one {@link GTNetProtocolDescriptor} per message code.
 *
 * <p>
 * Category, request/response relationship, payload model, form eligibility, retry policy, delete protection and rule
 * eligibility are all answered from here. Nothing else in the system may restate any of them — the frontend receives
 * the descriptor set over the API instead of keeping its own copy, and the repository derives the list of codes that
 * await a reply instead of hardcoding it.
 * </p>
 *
 * <p>
 * The core codes are registered during construction from {@link CoreProtocolDescriptors}. An application adds its own
 * codes at start-up with {@link #register(GTNetProtocolDescriptor)}. {@code GTNetProtocolStartupValidator} then checks
 * that the result is complete before the server starts serving.
 * </p>
 */
@Component
public class GTNetMessageCodeRegistry {

  private static final Logger log = LoggerFactory.getLogger(GTNetMessageCodeRegistry.class);

  private final Map<Byte, GTNetProtocolDescriptor> descriptors = new ConcurrentHashMap<>();

  /** Derived from the descriptors on every registration, because it is read on every message overview. */
  private volatile List<Byte> requestCodesRequiringResponse = List.of();

  /** Creates the registry and registers the core protocol. */
  public GTNetMessageCodeRegistry() {
    CoreProtocolDescriptors.all().forEach(this::register);
    log.info("GTNet protocol registry initialized with {} core codes", descriptors.size());
  }

  /**
   * Registers one descriptor.
   *
   * @param descriptor the descriptor to register
   * @throws IllegalStateException if another code is already registered under the same wire value
   */
  public void register(GTNetProtocolDescriptor descriptor) {
    GTNetProtocolDescriptor existing = descriptors.putIfAbsent(descriptor.value(), descriptor);
    if (existing != null && existing.code() != descriptor.code()) {
      throw new IllegalStateException(String.format("Duplicate message code value %d: existing=%s, new=%s",
          descriptor.value(), existing.name(), descriptor.name()));
    }
    requestCodesRequiringResponse = descriptors.values().stream().filter(GTNetProtocolDescriptor::requiresResponse)
        .map(GTNetProtocolDescriptor::value).sorted().toList();
    log.debug("Registered message code {} with value {}", descriptor.name(), descriptor.value());
  }

  /**
   * The descriptor of a code, by wire value.
   *
   * @param value the byte value to look up
   * @return the descriptor, or null when the code is unknown
   */
  public GTNetProtocolDescriptor getDescriptor(byte value) {
    return descriptors.get(value);
  }

  /**
   * The descriptor of a code, by enum constant name.
   *
   * @param name the constant name, for example {@code GT_NET_ADMIN_MESSAGE_SEL_C}
   * @return the descriptor, or null when the name is unknown
   */
  public GTNetProtocolDescriptor getDescriptorByName(String name) {
    if (name == null) {
      return null;
    }
    return descriptors.values().stream().filter(descriptor -> descriptor.name().equals(name)).findFirst().orElse(null);
  }

  /**
   * Every registered descriptor.
   *
   * @return an unmodifiable view, in no particular order
   */
  public Collection<GTNetProtocolDescriptor> getAllDescriptors() {
    return Collections.unmodifiableCollection(descriptors.values());
  }

  /**
   * The wire values of all requests that stay open until an answer arrives.
   *
   * <p>
   * This replaces the hardcoded three-element list the repository used to carry, which omitted the token refresh and
   * the exchange sync — so a pending request of either kind never appeared in the pending map the reply gate reads, was
   * not delete-protected, and did not block the deletion of its peer.
   * </p>
   *
   * @return the codes, sorted by value
   */
  public List<Byte> requestCodesRequiringResponse() {
    return requestCodesRequiringResponse;
  }

  /**
   * The descriptors whose payload the user fills in, keyed by code.
   *
   * @return the form-eligible descriptors
   */
  public List<GTNetProtocolDescriptor> getFormEligibleDescriptors() {
    return descriptors.values().stream().filter(GTNetProtocolDescriptor::formEligible)
        .sorted((a, b) -> Byte.compare(a.value(), b.value())).collect(Collectors.toList());
  }

  /**
   * Looks up a message code by its byte value.
   *
   * @param value the byte value to look up
   * @return the corresponding GTNetMessageCode, or null if not found
   */
  public GTNetMessageCode getByValue(byte value) {
    GTNetProtocolDescriptor descriptor = descriptors.get(value);
    return descriptor == null ? null : descriptor.code();
  }

  /**
   * Looks up a message code by its enum constant name.
   *
   * @param name the enum constant name to look up
   * @return the corresponding GTNetMessageCode, or null if not found
   */
  public GTNetMessageCode getByName(String name) {
    GTNetProtocolDescriptor descriptor = getDescriptorByName(name);
    return descriptor == null ? null : descriptor.code();
  }

  /**
   * Returns the valid response codes for a given request code.
   *
   * @param requestCode the request message code
   * @return list of valid response codes, or empty list when the code answers nothing
   */
  public List<GTNetMessageCode> getValidResponses(GTNetMessageCode requestCode) {
    GTNetProtocolDescriptor descriptor = requestCode == null ? null : descriptors.get(requestCode.getValue());
    return descriptor == null ? List.of() : descriptor.validResponses();
  }

  /**
   * Whether a response code is a registered valid answer to a request code, compared by wire value.
   *
   * <p>
   * This is what separates an answer from a receipt. {@code GT_NET_ACK_S}, {@code GT_NET_DEFERRED_S} and
   * {@code GT_NET_ERROR_S} are outcomes of the transport and are never valid answers to a request, so a sender that
   * records only registered responses keeps a manually approved request open until the real decision arrives.
   * </p>
   *
   * @param requestCodeValue  the wire value of the request
   * @param responseCodeValue the wire value of the answer that came back
   * @return true when the answer is a registered valid response for that request
   */
  public boolean isValidResponse(byte requestCodeValue, byte responseCodeValue) {
    GTNetProtocolDescriptor descriptor = descriptors.get(requestCodeValue);
    return descriptor != null && descriptor.isValidResponse(responseCodeValue);
  }

  /**
   * Whether the code is registered as an answer to some request.
   *
   * @param responseCodeValue the wire value to classify
   * @return true when some registered request accepts this code as an answer
   */
  public boolean isRegisteredResponse(byte responseCodeValue) {
    return descriptors.values().stream().anyMatch(descriptor -> descriptor.isValidResponse(responseCodeValue));
  }
}
