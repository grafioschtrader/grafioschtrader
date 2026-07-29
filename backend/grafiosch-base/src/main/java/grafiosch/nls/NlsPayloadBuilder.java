package grafiosch.nls;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Turns a flat set of raw NLS property entries into the structure delivered to the client, applying
 * {@link NlsKeyMapper} to every key and rejecting any mapping that is not unique.
 *
 * <p>
 * The result is a {@link SortedMap} rather than an arbitrary map on purpose. The payload is hashed into an
 * {@code ETag}, is diffed against a checked-in baseline during the migration, and decides which of two colliding keys
 * wins; all three need a deterministic iteration order, which a hash-based container does not provide.
 * </p>
 */
public final class NlsPayloadBuilder {

  private NlsPayloadBuilder() {
  }

  /**
   * Builds the client payload.
   *
   * @param rawEntries the merged property entries, keyed by raw property key
   * @return an ordered map whose values are either a {@code String} or, for an allow-listed namespace, a nested
   *         {@code SortedMap} of strings
   * @throws NlsMappingCollisionException if two raw keys map to the same client key, or if a namespace name is also
   *                                      used as a plain key
   */
  public static SortedMap<String, Object> build(Map<String, String> rawEntries) {
    SortedMap<String, Object> payload = new TreeMap<>();
    Map<String, String> originOfClientKey = new HashMap<>();
    List<String> collisions = new ArrayList<>();
    // Iterating in raw-key order keeps the reported collision text stable regardless of the caller's map type.
    for (Map.Entry<String, String> entry : new TreeMap<>(rawEntries).entrySet()) {
      String rawKey = entry.getKey();
      switch (NlsKeyMapper.map(rawKey)) {
        case ClientKey.Flat(String clientKey) ->
          putLeaf(payload, clientKey, clientKey, rawKey, entry.getValue(), originOfClientKey, collisions);
        case ClientKey.Nested(String namespace, String leaf) ->
          putNestedLeaf(payload, namespace, leaf, rawKey, entry.getValue(), originOfClientKey, collisions);
      }
    }
    if (!collisions.isEmpty()) {
      throw new NlsMappingCollisionException(collisions);
    }
    return payload;
  }

  @SuppressWarnings("unchecked")
  private static void putNestedLeaf(SortedMap<String, Object> payload, String namespace, String leaf, String rawKey,
      String value, Map<String, String> originOfClientKey, List<String> collisions) {
    Object node = payload.computeIfAbsent(namespace, _ -> new TreeMap<String, Object>());
    if (!(node instanceof SortedMap)) {
      collisions.add(namespace + " <- " + originOfClientKey.get(namespace) + ", " + rawKey
          + " (a namespace cannot also be a plain key)");
      return;
    }
    putLeaf((SortedMap<String, Object>) node, leaf, namespace + "." + leaf, rawKey, value, originOfClientKey,
        collisions);
  }

  private static void putLeaf(SortedMap<String, Object> target, String leafKey, String reportedKey, String rawKey,
      String value, Map<String, String> originOfClientKey, List<String> collisions) {
    String previousRawKey = originOfClientKey.put(reportedKey, rawKey);
    if (previousRawKey != null) {
      collisions.add(reportedKey + " <- " + previousRawKey + ", " + rawKey);
      return;
    }
    target.put(leafKey, value);
  }

  /**
   * Structural check of the nesting contract, without building a payload or touching the file system. Used by the
   * build-time guard so that a violation is reported as a list rather than as the first exception thrown.
   *
   * <p>
   * Enforced invariants: a namespace name is never also used as a plain key (that would make the client read a string
   * where it expects an object), and a nested key has exactly one segment below its namespace (a second level would
   * require recursion and an escaping convention that this contract deliberately avoids).
   * </p>
   *
   * @param rawKeys the merged raw property keys to check
   * @return one description per violation, empty when the contract holds
   */
  public static List<String> validateNesting(Set<String> rawKeys) {
    List<String> violations = new ArrayList<>();
    Set<String> namespaces = NlsMappingRegistry.nestedNamespaces();
    for (String rawKey : new TreeSet<>(rawKeys)) {
      if (namespaces.contains(rawKey)) {
        violations.add(rawKey + " is a nested namespace and must not also exist as a plain key");
        continue;
      }
      if (NlsKeyMapper.map(rawKey) instanceof ClientKey.Nested(String namespace, String leaf)
          && leaf.indexOf('.') >= 0) {
        violations.add(rawKey + " nests more than one level below '" + namespace + "' (leaf '" + leaf + "')");
      }
    }
    return violations;
  }
}
