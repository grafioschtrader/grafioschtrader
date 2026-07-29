package grafioschtrader.nls;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafiosch.nls.NlsBundleGuard;
import grafiosch.nls.NlsBundleInspector;
import grafiosch.nls.NlsResourceLists;

/**
 * Guards both bundle pairs that make up the delivered texts. This module is the only one that sees all four
 * {@code messages*.properties} files on a single class path, so the cross-module checks can only live here.
 *
 * <p>
 * The corresponding library-only guard is {@code grafiosch.nls.NlsBaseBundleGuardTest}, which covers the grafiosch-base
 * pair when the application is not built at all.
 * </p>
 */
class NlsBundleGuardTest {

  @Test
  @DisplayName("Both bundle pairs satisfy parity, encoding, key and placeholder rules")
  void allBundlePairsAreSound() {
    List<String> violations = new ArrayList<>();
    violations.addAll(NlsBundleGuard.checkBundlePair(NlsBundleInspector.BASE_BUNDLE,
        NlsResourceLists.readLines(NlsResourceLists.BASE_EN_REUSE)));
    violations.addAll(NlsBundleGuard.checkBundlePair(NlsBundleInspector.COMMON_BUNDLE,
        NlsResourceLists.readLines(NlsResourceLists.COMMON_EN_REUSE)));
    assertThat(violations)
        .as("NLS guard violations:%n%s", String.join(System.lineSeparator(), violations)).isEmpty();
  }
}
