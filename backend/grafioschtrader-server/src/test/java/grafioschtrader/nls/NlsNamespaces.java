package grafioschtrader.nls;

import java.util.LinkedHashMap;
import java.util.Map;

import grafiosch.nls.NlsBundleInspector;
import grafiosch.nls.NlsBundleInspector.Bundle;

/**
 * Shared helper for the NLS guards of this module: merges the two bundle pairs the way the running server merges them.
 */
final class NlsNamespaces {

  /** The languages that must be delivered completely; the English bundle is also the fallback. */
  static final String[] LANGUAGES = { null, "de" };

  private NlsNamespaces() {
  }

  /**
   * Merges the application bundle over the library bundle for one language, matching the basename order configured in
   * {@code MessageConfig}: the application wins, so a generic library text can be relabelled by the application.
   *
   * @param language {@code null} for English, otherwise a language tag such as {@code de}
   * @return the merged raw entries, keyed by raw property key
   */
  static Map<String, String> mergedEntries(String language) {
    Bundle library = NlsBundleInspector.load(NlsBundleInspector.BASE_BUNDLE, language);
    Bundle application = NlsBundleInspector.load(NlsBundleInspector.COMMON_BUNDLE, language);
    Map<String, String> merged = new LinkedHashMap<>(library.entries());
    merged.putAll(application.entries());
    return merged;
  }

  /**
   * @param language {@code null} for English, otherwise a language tag such as {@code de}
   * @return a readable name of the language, for assertion messages
   */
  static String languageName(String language) {
    return language == null ? "en" : language;
  }
}
