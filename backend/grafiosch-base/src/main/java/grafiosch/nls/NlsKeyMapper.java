package grafiosch.nls;

import java.util.Locale;

/**
 * Translates a raw NLS property key into the form the client addresses it by.
 *
 * <p>
 * The backend and the frontend derive their key from the same camelCase field name, but by different mechanical rules:
 * the backend uses {@code RestHelper.camelCaseToDotSeparated()} ({@code readableName} to {@code readable.name}) because
 * that is what {@code MessageSource} resolves server-side, while the frontend uses
 * {@code AppHelper.toUpperCaseWithUnderscore()} ({@code readableName} to {@code READABLE_NAME}) because that is what
 * {@code TranslateService} resolves. This class is the bridge that lets a single properties entry serve both lookups.
 * </p>
 *
 * <p>
 * The rules are ordered and the order is significant:
 * </p>
 * <ol>
 * <li>{@value #CLIENT_PREFIX} - strip the prefix and emit the remainder verbatim. Marks a key that is consumed only by
 * the client and is never resolved server-side, such as a dynamic-form validator message. The prefix exists because
 * shape alone cannot separate the two dialects: {@code webUrl} is simultaneously a real server-resolved Bean Validation
 * message (see {@code grafiosch.validation.WebUrl}) and a frontend validator key with a different text, so
 * {@code c.webUrl} yields the client key {@code webUrl} while the server-side {@code webUrl} yields {@code WEBURL}.</li>
 * <li>A registered pass-through prefix ({@code g.}, {@code gt.}, {@code UDF_}) - emit verbatim, because those keys name
 * configuration parameters and metadata rather than being derived from a field name.</li>
 * <li>A registered nested namespace - split on the <em>first</em> dot only and emit a one-level object.</li>
 * <li>Everything else - upper-case and replace dots with underscores.</li>
 * </ol>
 *
 * <p>
 * Rule 4 is idempotent on keys that are already {@code UPPER_SNAKE_CASE}, which is what allows a key to be moved from
 * the frontend translation files into a properties file unchanged. Note that it must not key off "contains a dot":
 * roughly two dozen field labels ({@code name}, {@code date}, {@code email}, {@code isin}, ...) are single lower-case
 * words that still have to become {@code UPPER_SNAKE_CASE}.
 * </p>
 */
public final class NlsKeyMapper {

  /**
   * Prefix marking a key that only the client resolves. The prefix is stripped, so the remainder is delivered exactly
   * as written, including a lower-case or dotted shape that rule 4 would otherwise destroy.
   */
  public static final String CLIENT_PREFIX = "c.";

  private NlsKeyMapper() {
  }

  /**
   * Applies the ordered rule set to one raw property key.
   *
   * @param rawKey the key exactly as it appears in a {@code messages*.properties} file
   * @return the client-facing key; never {@code null}
   */
  public static ClientKey map(String rawKey) {
    if (rawKey.startsWith(CLIENT_PREFIX)) {
      return new ClientKey.Flat(rawKey.substring(CLIENT_PREFIX.length()));
    }
    for (String prefix : NlsMappingRegistry.passThroughPrefixes()) {
      if (rawKey.startsWith(prefix)) {
        return new ClientKey.Flat(rawKey);
      }
    }
    int firstDot = rawKey.indexOf('.');
    if (firstDot > 0) {
      String namespace = rawKey.substring(0, firstDot);
      if (NlsMappingRegistry.nestedNamespaces().contains(namespace)) {
        return new ClientKey.Nested(namespace, rawKey.substring(firstDot + 1));
      }
    }
    // Locale.ROOT is required: under a Turkish default locale the locale-sensitive overload maps 'i' to 'İ' and would
    // silently mangle every key containing an i, for example isin, nickname and email.
    return new ClientKey.Flat(rawKey.toUpperCase(Locale.ROOT).replace('.', '_'));
  }

  /**
   * Convenience form of {@link #map(String)} for manifests, guards and log messages, which compare keys as text.
   *
   * @param rawKey the key exactly as it appears in a {@code messages*.properties} file
   * @return the flat client key, or {@code namespace.leaf} for a nested key
   */
  public static String mapToString(String rawKey) {
    return map(rawKey).asString();
  }
}
