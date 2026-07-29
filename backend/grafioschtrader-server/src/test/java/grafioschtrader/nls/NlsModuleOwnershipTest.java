package grafioschtrader.nls;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafiosch.nls.NlsBundleInspector;
import grafiosch.nls.NlsBundleInspector.Bundle;

/**
 * Keeps text ownership unambiguous: each key belongs to exactly one module, and which module that is, is expressed by
 * which {@code messages*.properties} file holds it.
 *
 * <p>
 * A key defined in both modules still resolves -- the application bundle wins -- but the losing copy is invisible: it
 * can be edited forever without any effect, and it is the natural place for the two texts to drift apart. The one
 * deliberate exception is the key that demonstrates the precedence rule itself.
 * </p>
 */
class NlsModuleOwnershipTest {

  /**
   * Defined in both modules on purpose. {@code id.user.redirect} is the administrator-facing label in the library and
   * the user-facing one in the application; overriding it is exactly what the basename order exists for, and
   * {@link NlsModulePrecedenceTest} asserts the application copy wins.
   */
  private static final Set<String> INTENTIONAL_OVERRIDES = Set.of("id.user.redirect");

  @Test
  @DisplayName("No key is defined in both modules unless the override is intentional")
  void modulesDoNotOverlap() {
    for (String language : NlsNamespaces.LANGUAGES) {
      Bundle library = NlsBundleInspector.load(NlsBundleInspector.BASE_BUNDLE, language);
      Bundle application = NlsBundleInspector.load(NlsBundleInspector.COMMON_BUNDLE, language);
      Set<String> overlap = new TreeSet<>(library.keys());
      overlap.retainAll(application.keys());
      overlap.removeAll(INTENTIONAL_OVERRIDES);
      assertThat(overlap).as("Language '%s': these keys exist in grafiosch-base AND grafioschtrader-common. The "
          + "application copy wins, so the library copy is dead text. Delete one of the two, or add the key to "
          + "INTENTIONAL_OVERRIDES if the override is deliberate.", NlsNamespaces.languageName(language)).isEmpty();
    }
  }

  @Test
  @DisplayName("Every intentional override really is defined in both modules")
  void intentionalOverridesAreStillReal() {
    Set<String> library = NlsBundleInspector.load(NlsBundleInspector.BASE_BUNDLE, null).keys();
    Set<String> application = NlsBundleInspector.load(NlsBundleInspector.COMMON_BUNDLE, null).keys();
    for (String key : INTENTIONAL_OVERRIDES) {
      assertThat(library).as("stale entry in INTENTIONAL_OVERRIDES: '%s' is no longer in grafiosch-base", key)
          .contains(key);
      assertThat(application).as("stale entry in INTENTIONAL_OVERRIDES: '%s' is no longer in grafioschtrader-common",
          key).contains(key);
    }
  }
}
