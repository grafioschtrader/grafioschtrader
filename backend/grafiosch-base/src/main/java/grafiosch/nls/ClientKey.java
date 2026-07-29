package grafiosch.nls;

/**
 * The client-facing form of a backend NLS key, as produced by {@link NlsKeyMapper#map(String)}.
 *
 * <p>
 * Almost every key becomes a {@link Flat} entry at the top level of the JSON payload delivered by the language endpoint.
 * A small, explicitly allow-listed set of namespaces becomes a {@link Nested} entry instead, because the consuming
 * frontend code reads them as an object rather than as a string (for example {@code translateService.get('GT_FILTER')}
 * followed by an index access on the result).
 * </p>
 */
public sealed interface ClientKey {

  /**
   * Renders the key the way it is addressed by the client, so that a flat key and a nested key can be compared,
   * sorted and written to the ownership manifest in one uniform notation.
   *
   * @return the flat key, or {@code namespace.leaf} for a nested key
   */
  String asString();

  /**
   * A key that lives at the top level of the payload.
   *
   * @param key the client key, already transformed according to the mapping rules
   */
  record Flat(String key) implements ClientKey {
    @Override
    public String asString() {
      return key;
    }
  }

  /**
   * A key that lives one level down, below an allow-listed namespace object.
   *
   * <p>
   * Nesting is deliberately limited to exactly one level: the raw key is split on its <em>first</em> dot only, so the
   * leaf is a single value and neither escaping nor recursive object building is required.
   * </p>
   *
   * @param namespace the top-level object name, which must be registered in {@link NlsMappingRegistry}
   * @param leaf      the property name inside that object
   */
  record Nested(String namespace, String leaf) implements ClientKey {
    @Override
    public String asString() {
      return namespace + "." + leaf;
    }
  }
}
