package grafiosch.gtnet;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Registry for exchange kind types that supports application-specific implementations.
 *
 * This registry provides a unified lookup mechanism for exchange kinds, allowing application
 * modules to register their own {@link IExchangeKindType} implementations and retrieve them
 * by their byte value.
 *
 * <h3>Usage</h3>
 * Application-specific exchange kinds should be registered at startup via
 * {@link #registerExchangeKind(IExchangeKindType)}. Use {@link #getByValue(byte)} to retrieve
 * exchange kinds by their byte value.
 *
 * <h3>Example</h3>
 * <pre>
 * // At startup (e.g., in GTStartUp)
 * for (GTNetExchangeKindType kind : GTNetExchangeKindType.values()) {
 *     exchangeKindTypeRegistry.registerExchangeKind(kind);
 * }
 *
 * // Later in code
 * IExchangeKindType kind = exchangeKindTypeRegistry.getByValue((byte) 0);
 * </pre>
 */
@Component
public class ExchangeKindTypeRegistry {

  private static final Logger log = LoggerFactory.getLogger(ExchangeKindTypeRegistry.class);

  private final Map<Byte, IExchangeKindType> kindsByValue = new ConcurrentHashMap<>();

  /**
   * Registers an exchange kind type in the registry.
   *
   * @param kind the exchange kind to register
   * @throws IllegalStateException if a kind with the same value is already registered with a different instance
   */
  public void registerExchangeKind(IExchangeKindType kind) {
    IExchangeKindType existing = kindsByValue.putIfAbsent(kind.getValue(), kind);
    if (existing != null && existing != kind) {
      throw new IllegalStateException(String.format(
          "Duplicate exchange kind value %d: existing=%s, new=%s",
          kind.getValue(), existing.name(), kind.name()));
    }
    log.debug("Registered exchange kind {} with value {}", kind.name(), kind.getValue());
  }

  /**
   * Looks up an exchange kind by its byte value.
   *
   * @param value the byte value to look up
   * @return the corresponding IExchangeKindType, or null if not found
   */
  public IExchangeKindType getByValue(byte value) {
    return kindsByValue.get(value);
  }

  /**
   * Looks up an exchange kind by its byte value with a default fallback.
   *
   * @param value the byte value to look up
   * @param defaultKind the default kind to return if not found
   * @return the corresponding IExchangeKindType, or defaultKind if not found
   */
  public IExchangeKindType getByValue(byte value, IExchangeKindType defaultKind) {
    IExchangeKindType kind = kindsByValue.get(value);
    return kind != null ? kind : defaultKind;
  }

  /**
   * Checks if an exchange kind with the given value is registered.
   *
   * @param value the byte value to check
   * @return true if a kind with this value is registered
   */
  public boolean hasKind(byte value) {
    return kindsByValue.containsKey(value);
  }

  /**
   * Returns all registered exchange kinds.
   *
   * @return unmodifiable collection of all registered kinds
   */
  public Collection<IExchangeKindType> getAllKinds() {
    return Collections.unmodifiableCollection(kindsByValue.values());
  }

  /**
   * Returns the number of registered exchange kinds.
   *
   * @return the count of registered kinds
   */
  public int size() {
    return kindsByValue.size();
  }

  /**
   * Returns the default exchange kinds used when no specific kinds are requested.
   * Returns kinds where {@link IExchangeKindType#isSyncable()} returns true.
   *
   * @return set of syncable exchange kinds, or empty set if none registered
   */
  public java.util.Set<IExchangeKindType> getDefaultKinds() {
    return getSyncableKinds();
  }

  /**
   * Returns all exchange kinds that participate in bulk synchronization.
   * These are kinds where {@link IExchangeKindType#isSyncable()} returns true.
   *
   * Syncable kinds participate in data request/accept/revoke flows. Non-syncable kinds
   * (like SECURITY_METADATA) use on-demand lookup patterns.
   *
   * @return set of syncable exchange kinds
   */
  public java.util.Set<IExchangeKindType> getSyncableKinds() {
    return kindsByValue.values().stream()
        .filter(IExchangeKindType::isSyncable)
        .collect(java.util.stream.Collectors.toSet());
  }

  /**
   * Looks up an exchange kind by its enum name (case-insensitive).
   *
   * @param name the enum constant name to look up
   * @return the corresponding IExchangeKindType, or null if not found
   */
  public IExchangeKindType getByName(String name) {
    if (name == null || name.isBlank()) {
      return null;
    }
    String upperName = name.trim().toUpperCase();
    return kindsByValue.values().stream()
        .filter(kind -> kind.name().equalsIgnoreCase(upperName))
        .findFirst()
        .orElse(null);
  }

  /**
   * Parses an exchange kind from a string value (either numeric byte value or enum name).
   *
   * @param value the string to parse (e.g., "0", "LAST_PRICE")
   * @return the corresponding IExchangeKindType, or null if not found
   */
  public IExchangeKindType parse(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      byte numericValue = Byte.parseByte(value.trim());
      return getByValue(numericValue);
    } catch (NumberFormatException e) {
      return getByName(value);
    }
  }
}
