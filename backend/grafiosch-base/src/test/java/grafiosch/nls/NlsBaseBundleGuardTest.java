package grafiosch.nls;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the bundle pair owned by grafiosch-base on its own, so that the reusable library stays sound even when it is
 * built without the grafioschtrader application.
 *
 * <p>
 * The equivalent guard covering both bundle pairs together, plus the cross-module mapping-collision check, lives in
 * {@code grafioschtrader-server}, which is the only module that sees all four files on one class path.
 * </p>
 */
class NlsBaseBundleGuardTest {

  @Test
  @DisplayName("grafiosch-base bundle pair satisfies parity, encoding, key and placeholder rules")
  void baseBundlePairIsSound() {
    List<String> violations = NlsBundleGuard.checkBundlePair(NlsBundleInspector.BASE_BUNDLE,
        NlsResourceLists.readLines(NlsResourceLists.BASE_EN_REUSE));
    assertThat(violations).as("NLS guard violations in grafiosch-base:%n%s", String.join(System.lineSeparator(),
        violations)).isEmpty();
  }

  @Test
  @DisplayName("grafiosch-base keys map without collision")
  void baseKeysMapUniquely() {
    for (String language : new String[] { null, "de" }) {
      var bundle = NlsBundleInspector.load(NlsBundleInspector.BASE_BUNDLE, language);
      assertThat(NlsPayloadBuilder.validateNesting(bundle.keys()))
          .as("nesting contract violated in %s", bundle.resource()).isEmpty();
      NlsPayloadBuilder.build(bundle.entries());
    }
  }
}
