package grafiosch.nls;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * Registry of the module-contributed inputs to the NLS key-mapping contract of {@link NlsKeyMapper}.
 *
 * <p>
 * Each module that participates ships one descriptor at {@value #DESCRIPTOR_RESOURCE}. The registry reads
 * <em>every</em> such resource visible to the class loader and unions the declared values, so a module extends the
 * contract by adding a resource file rather than by executing registration code.
 * </p>
 *
 * <p>
 * That matters for correctness, not only for convenience: the comparable existing mechanism
 * {@code BaseConstants.PREFIXES_PARAM} is populated from a {@code @PostConstruct} method, so any check that does not
 * boot the Spring context would see an incomplete registry and would therefore verify a mapping that differs from the
 * one the running server applies. Reading the descriptors from the class path gives the runtime and the build-time
 * guards byte-identical inputs with no ordering requirement.
 * </p>
 */
public final class NlsMappingRegistry {

  /** Class path location every participating module may provide exactly once. */
  public static final String DESCRIPTOR_RESOURCE = "META-INF/grafiosch/nls-mapping.properties";

  private static final String PROP_PASSTHROUGH_PREFIXES = "passthrough.prefixes";
  private static final String PROP_NESTED_NAMESPACES = "nested.namespaces";
  private static final Set<String> KNOWN_PROPERTIES = Set.of(PROP_PASSTHROUGH_PREFIXES, PROP_NESTED_NAMESPACES);

  private static volatile Registry registry;

  private NlsMappingRegistry() {
  }

  /**
   * Key prefixes whose keys are delivered to the client unchanged, such as {@code g.} and {@code gt.} for configuration
   * parameters and {@code UDF_} for user-defined field metadata.
   *
   * @return the unioned, immutable set of pass-through prefixes
   */
  public static Set<String> passThroughPrefixes() {
    return load().prefixes();
  }

  /**
   * Top-level names below which keys are delivered as a one-level object instead of as a flat entry, because the
   * consuming frontend code reads them as an object.
   *
   * @return the unioned, immutable set of nested namespace names
   */
  public static Set<String> nestedNamespaces() {
    return load().namespaces();
  }

  /**
   * Discards the memoized registry so the next access re-reads the descriptors. Intended for tests that manipulate the
   * class path or the context class loader; production code never needs this.
   */
  static void reset() {
    registry = null;
  }

  private static Registry load() {
    Registry local = registry;
    if (local == null) {
      synchronized (NlsMappingRegistry.class) {
        local = registry;
        if (local == null) {
          local = readDescriptors();
          registry = local;
        }
      }
    }
    return local;
  }

  private static Registry readDescriptors() {
    Set<String> prefixes = new TreeSet<>();
    Set<String> namespaces = new TreeSet<>();
    ClassLoader classLoader = NlsMappingRegistry.class.getClassLoader();
    try {
      List<URL> descriptors = Collections.list(classLoader.getResources(DESCRIPTOR_RESOURCE));
      for (URL descriptor : descriptors) {
        Properties properties = new Properties();
        try (InputStream inputStream = descriptor.openStream()) {
          properties.load(inputStream);
        }
        rejectUnknownProperties(descriptor, properties);
        prefixes.addAll(splitList(properties.getProperty(PROP_PASSTHROUGH_PREFIXES)));
        namespaces.addAll(splitList(properties.getProperty(PROP_NESTED_NAMESPACES)));
      }
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read NLS mapping descriptors " + DESCRIPTOR_RESOURCE, e);
    }
    return new Registry(Set.copyOf(prefixes), Set.copyOf(namespaces));
  }

  /**
   * Fails on a property name the registry does not understand, so that a typo in a descriptor surfaces as a build
   * failure instead of silently dropping a prefix and changing every key that depended on it.
   */
  private static void rejectUnknownProperties(URL descriptor, Properties properties) {
    Set<String> unknown = new LinkedHashSet<>(properties.stringPropertyNames());
    unknown.removeAll(KNOWN_PROPERTIES);
    if (!unknown.isEmpty()) {
      throw new IllegalStateException(
          "Unknown propert" + (unknown.size() == 1 ? "y " : "ies ") + unknown + " in " + descriptor + ", expected one of "
              + new TreeSet<>(KNOWN_PROPERTIES));
    }
  }

  private static Set<String> splitList(String value) {
    if (value == null || value.isBlank()) {
      return Set.of();
    }
    return Arrays.stream(value.split(",")).map(String::trim).filter(entry -> !entry.isEmpty())
        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
  }

  private record Registry(Set<String> prefixes, Set<String> namespaces) {
  }
}
