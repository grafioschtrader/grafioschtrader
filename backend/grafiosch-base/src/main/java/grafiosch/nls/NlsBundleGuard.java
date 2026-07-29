package grafiosch.nls;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import grafiosch.nls.NlsBundleInspector.Bundle;

/**
 * Checks that one {@code messages*.properties} bundle pair satisfies the rules the delivered NLS payload depends on.
 *
 * <p>
 * The checks return a list of violations instead of asserting, so that the same implementation can back the guard in
 * {@code grafiosch-base} (which sees only the library bundle) and the guard in {@code grafioschtrader-server} (which
 * sees both bundles), and so that a failure reports every problem at once rather than only the first.
 * </p>
 */
public final class NlsBundleGuard {

  /**
   * Characters a key may consist of. Note that {@code |} is deliberately allowed: real keys such as
   * {@code CREATE|STANDING_ORDER} use it as a separator. A space, by contrast, is rejected, because a space silently
   * terminates the key in {@code .properties} syntax and would produce a truncated key at runtime.
   */
  private static final Pattern LEGAL_KEY = Pattern.compile("[A-Za-z0-9_.|]+");

  /** A {@code MessageFormat} argument, which is what marks a value as resolved server-side. */
  private static final Pattern MESSAGE_FORMAT_ARGUMENT = Pattern.compile("\\{\\d+[^}]*}");

  private NlsBundleGuard() {
  }

  /**
   * Runs every per-bundle-pair check.
   *
   * @param bundleBaseName    class path base name, for example {@link NlsBundleInspector#BASE_BUNDLE}
   * @param enReuseAllowlist  keys whose German value may deliberately equal the English one
   * @return one description per violation, empty when the pair is sound
   */
  public static List<String> checkBundlePair(String bundleBaseName, Set<String> enReuseAllowlist) {
    Bundle english = NlsBundleInspector.load(bundleBaseName, null);
    Bundle german = NlsBundleInspector.load(bundleBaseName, "de");
    List<String> violations = new ArrayList<>();
    violations.addAll(checkDuplicates(english));
    violations.addAll(checkDuplicates(german));
    violations.addAll(checkKeyCharset(english));
    violations.addAll(checkKeyCharset(german));
    violations.addAll(checkParity(english, german));
    violations.addAll(checkEnReuse(english, german, enReuseAllowlist));
    violations.addAll(checkPlaceholderDialect(english));
    violations.addAll(checkPlaceholderDialect(german));
    return violations;
  }

  /**
   * A key must occur at most once per file. {@link java.util.Properties#load} keeps the last occurrence silently, so a
   * duplicate is invisible at runtime and shows up only as a text that will not change no matter which entry is edited.
   */
  public static List<String> checkDuplicates(Bundle bundle) {
    return bundle.duplicateKeys().stream().map(key -> bundle.resource() + ": key '" + key + "' is defined more than once")
        .toList();
  }

  /**
   * A key must be expressible in {@code .properties} syntax without escaping, so the files stay readable and a key can
   * be grepped for as written.
   */
  public static List<String> checkKeyCharset(Bundle bundle) {
    return bundle.keys().stream().filter(key -> !LEGAL_KEY.matcher(key).matches())
        .map(key -> bundle.resource() + ": key '" + key + "' contains a character that is not allowed in a key; "
            + "allowed are letters, digits, '_', '.' and '|'")
        .toList();
  }

  /**
   * Both languages must define exactly the same keys. The English bundle stays the {@code ResourceBundle} fallback, so
   * a gap would surface as an English text rather than as an error; strict parity at build time is what guarantees no
   * such gap ever reaches a user.
   */
  public static List<String> checkParity(Bundle english, Bundle german) {
    List<String> violations = new ArrayList<>();
    Set<String> onlyEnglish = new TreeSet<>(english.keys());
    onlyEnglish.removeAll(german.keys());
    Set<String> onlyGerman = new TreeSet<>(german.keys());
    onlyGerman.removeAll(english.keys());
    onlyEnglish.forEach(key -> violations.add(german.resource() + ": missing key '" + key + "' (present in "
        + english.resource() + ")"));
    onlyGerman.forEach(key -> violations.add(english.resource() + ": missing key '" + key + "' (present in "
        + german.resource() + ")"));
    return violations;
  }

  /**
   * A German value identical to the English one is usually a forgotten translation. Where it is deliberate, such as a
   * provider's subscription tier name, the key is listed in the module's {@code nls-en-reuse.txt}. A stale entry is a
   * violation too, so the allow list cannot silently accumulate keys that have since been translated or removed.
   */
  public static List<String> checkEnReuse(Bundle english, Bundle german, Set<String> enReuseAllowlist) {
    List<String> violations = new ArrayList<>();
    Set<String> unusedAllowlistEntries = new LinkedHashSet<>(enReuseAllowlist);
    for (Map.Entry<String, String> entry : english.entries().entrySet()) {
      String key = entry.getKey();
      String germanValue = german.entries().get(key);
      if (germanValue == null || !germanValue.equals(entry.getValue())) {
        continue;
      }
      unusedAllowlistEntries.remove(key);
      if (!enReuseAllowlist.contains(key)) {
        violations.add(german.resource() + ": '" + key + "' has the same value as English (\"" + entry.getValue()
            + "\"). Translate it, or list the key in the module's nls-en-reuse.txt if the equal value is intended.");
      }
    }
    unusedAllowlistEntries
        .forEach(key -> violations.add("nls-en-reuse.txt for " + english.resource() + ": stale entry '" + key
            + "' -- the key no longer exists or the values are no longer equal."));
    return violations;
  }

  /**
   * The two placeholder dialects must not be mixed on one key. Values resolved server-side pass through
   * {@code MessageFormat}, which uses {@code {0}} and treats a single quote as an escape character; values resolved by
   * ngx-translate in the browser use {@code {{name}}} and take a single quote literally. A key that mixes them renders
   * wrongly on at least one of the two paths.
   */
  public static List<String> checkPlaceholderDialect(Bundle bundle) {
    List<String> violations = new ArrayList<>();
    for (Map.Entry<String, String> entry : bundle.entries().entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      if (value == null) {
        continue;
      }
      boolean hasMessageFormatArgument = MESSAGE_FORMAT_ARGUMENT.matcher(value).find();
      if (value.contains("{{") && hasMessageFormatArgument) {
        violations.add(bundle.resource() + ": '" + key + "' mixes the ngx-translate {{name}} and the MessageFormat {0} "
            + "placeholder dialect.");
      }
      if (hasMessageFormatArgument && value.replace("''", "").indexOf('\'') >= 0) {
        violations.add(bundle.resource() + ": '" + key + "' is resolved by MessageFormat but contains a single quote "
            + "that is not doubled; MessageFormat would swallow it and the placeholder would not be replaced.");
      }
      if (key.startsWith(NlsKeyMapper.CLIENT_PREFIX) && hasMessageFormatArgument) {
        violations.add(bundle.resource() + ": '" + key + "' is a client-only key but uses a MessageFormat placeholder; "
            + "client keys are never resolved server-side, so use the {{name}} form.");
      }
    }
    return violations;
  }
}
