package grafiosch.nls;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Reads the small plain-text side files that accompany a bundle pair, currently the EN-reuse allow list.
 *
 * <p>
 * Each such file is a class path resource of the module that owns the keys, so that ownership is expressed by the
 * file's location. That is also what keeps the reusable library checkable on its own: a single shared file would have
 * to be visible to {@code grafiosch-test-integration}, which must not see any {@code grafioschtrader-*} artifact.
 * </p>
 */
public final class NlsResourceLists {

  /** EN-reuse allow list belonging to the grafiosch-base bundle pair. */
  public static final String BASE_EN_REUSE = "i18n/nls-en-reuse.txt";
  /** EN-reuse allow list belonging to the grafioschtrader-common bundle pair. */
  public static final String COMMON_EN_REUSE = "message/nls-en-reuse.txt";

  private NlsResourceLists() {
  }

  /**
   * Reads a one-entry-per-line resource, ignoring blank lines and {@code #} comments.
   *
   * @param resource the class path resource name
   * @return the listed entries, in file order
   * @throws IllegalStateException if the resource is missing, ambiguous, or not valid UTF-8
   */
  public static Set<String> readLines(String resource) {
    Set<String> entries = new LinkedHashSet<>();
    for (String line : NlsBundleInspector.readUtf8(resource).split("\\R", -1)) {
      String trimmed = line.trim();
      if (!trimmed.isEmpty() && trimmed.charAt(0) != '#') {
        entries.add(trimmed);
      }
    }
    return entries;
  }
}
