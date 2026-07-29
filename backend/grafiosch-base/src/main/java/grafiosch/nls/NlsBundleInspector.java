package grafiosch.nls;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Reads one {@code messages*.properties} bundle exactly as it sits on the class path, keeping the information a
 * {@code MessageSource} discards.
 *
 * <p>
 * The guards need three things a merged {@code MessageSource} cannot provide: the key set of a <em>single</em> file
 * (for per-module ownership and per-language parity), the raw bytes (to prove the file is valid UTF-8 rather than
 * silently substituted), and duplicate keys within one file ({@link Properties#load} keeps the last occurrence without
 * complaint, so a duplicate is invisible afterwards).
 * </p>
 *
 * <p>
 * Reading happens through the class loader rather than through a relative source path. A source path such as
 * {@code ../grafiosch-base/src/main/resources/...} happens to work under Surefire but couples the check to the checkout
 * layout and breaks as soon as the module is consumed as a released jar. The class path resolves to the very artifact
 * the server loads.
 * </p>
 */
public final class NlsBundleInspector {

  /** Class path location of the bundle pair owned by grafiosch-base. */
  public static final String BASE_BUNDLE = "i18n/messages";
  /** Class path location of the bundle pair owned by grafioschtrader-common. */
  public static final String COMMON_BUNDLE = "message/messages";

  private NlsBundleInspector() {
  }

  /**
   * One bundle file with everything the guards need to judge it.
   *
   * @param resource      the class path resource that was read
   * @param entries       the parsed entries in file order
   * @param duplicateKeys keys that occur more than once in this file, in order of first occurrence
   */
  public record Bundle(String resource, Map<String, String> entries, List<String> duplicateKeys) {

    /**
     * @return the key set of this file, in file order
     */
    public Set<String> keys() {
      return entries.keySet();
    }
  }

  /**
   * Builds the class path resource name of one bundle file.
   *
   * @param bundle   base name such as {@link #BASE_BUNDLE}
   * @param language {@code null} or empty for the default (English) bundle, otherwise a language tag such as {@code de}
   * @return the resource name, for example {@code i18n/messages_de.properties}
   */
  public static String resourceName(String bundle, String language) {
    return bundle + (language == null || language.isEmpty() ? "" : "_" + language) + ".properties";
  }

  /**
   * Loads one bundle file.
   *
   * @param bundle   base name such as {@link #BASE_BUNDLE}
   * @param language {@code null} or empty for the default (English) bundle, otherwise a language tag such as {@code de}
   * @return the parsed bundle
   * @throws IllegalStateException if the resource is missing, is present more than once on the class path, or is not
   *                               valid UTF-8
   */
  public static Bundle load(String bundle, String language) {
    String resource = resourceName(bundle, language);
    String text = readUtf8(resource);
    Properties properties = new Properties();
    try {
      properties.load(new StringReader(text));
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot parse " + resource, e);
    }
    Map<String, String> entries = new LinkedHashMap<>();
    List<String> duplicateKeys = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    for (String key : scanKeys(text)) {
      if (!seen.add(key)) {
        duplicateKeys.add(key);
      }
      entries.put(key, properties.getProperty(key));
    }
    return new Bundle(resource, Collections.unmodifiableMap(entries), List.copyOf(duplicateKeys));
  }

  /**
   * Reads a class path resource and decodes it as strict UTF-8.
   *
   * <p>
   * A plain {@code new String(bytes, UTF_8)} would replace an invalid byte with {@code U+FFFD} and hide exactly the
   * encoding corruption of German umlauts this project keeps running into, so the decoder is configured to report
   * instead of substitute.
   * </p>
   *
   * @param resource the class path resource name
   * @return the decoded content
   * @throws IllegalStateException if the resource is missing, ambiguous, or not valid UTF-8
   */
  public static String readUtf8(String resource) {
    ClassLoader classLoader = NlsBundleInspector.class.getClassLoader();
    byte[] bytes;
    try {
      List<URL> urls = Collections.list(classLoader.getResources(resource));
      if (urls.size() != 1) {
        throw new IllegalStateException(
            "Expected exactly one " + resource + " on the class path but found " + urls.size() + ": " + urls);
      }
      try (InputStream inputStream = urls.get(0).openStream()) {
        bytes = inputStream.readAllBytes();
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read " + resource, e);
    }
    if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
      throw new IllegalStateException(resource + " starts with a UTF-8 BOM; Properties.load would read it as part of "
          + "the first key. Save the file without a BOM.");
    }
    try {
      return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    } catch (CharacterCodingException e) {
      throw new IllegalStateException(resource + " is not valid UTF-8; a German umlaut was most likely saved in a "
          + "single-byte encoding. See the encoding section in CLAUDE.md.", e);
    }
  }

  /**
   * Extracts the keys in file order, including duplicates, which {@link Properties#load} would collapse.
   *
   * @param text the decoded file content
   * @return every key in the order it appears
   */
  private static List<String> scanKeys(String text) {
    List<String> keys = new ArrayList<>();
    boolean insideContinuation = false;
    for (String line : text.split("\\R", -1)) {
      boolean continuesOnNextLine = endsWithOddBackslashCount(line);
      if (insideContinuation) {
        insideContinuation = continuesOnNextLine;
        continue;
      }
      insideContinuation = continuesOnNextLine;
      String trimmed = line.stripLeading();
      if (trimmed.isEmpty() || trimmed.charAt(0) == '#' || trimmed.charAt(0) == '!') {
        continue;
      }
      keys.add(extractKey(trimmed));
    }
    return keys;
  }

  /**
   * Reads the key part of a logical line, honouring the {@code .properties} rule that an escaped separator belongs to
   * the key and that the key ends at the first unescaped {@code =}, {@code :} or whitespace.
   */
  private static String extractKey(String line) {
    StringBuilder key = new StringBuilder();
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '\\' && i + 1 < line.length()) {
        key.append(c).append(line.charAt(++i));
        continue;
      }
      if (c == '=' || c == ':' || Character.isWhitespace(c)) {
        break;
      }
      key.append(c);
    }
    return key.toString();
  }

  private static boolean endsWithOddBackslashCount(String line) {
    int backslashes = 0;
    for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '\\'; i--) {
      backslashes++;
    }
    return backslashes % 2 == 1;
  }
}
