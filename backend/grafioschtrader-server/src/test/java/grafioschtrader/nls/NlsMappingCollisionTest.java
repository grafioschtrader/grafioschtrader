package grafioschtrader.nls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import grafiosch.nls.NlsPayloadBuilder;

/**
 * Fails when two raw property keys map to the same client key, because the payload can only carry one of the two texts
 * and which one survives is decided by iteration order rather than by intent.
 *
 * <p>
 * Both languages are checked separately and that is not redundant: the two bundles are edited independently, so a
 * collision can exist in German while English is clean. The German-only duplicate {@code reset.user_misused} against
 * {@code reset.user.misused} was exactly such a case.
 * </p>
 */
class NlsMappingCollisionTest {

  @ParameterizedTest(name = "language {0}")
  @NullSource
  @ValueSource(strings = "de")
  @DisplayName("Every raw key maps to a distinct client key")
  void keysMapUniquely(String language) {
    Map<String, String> merged = NlsNamespaces.mergedEntries(language);
    assertThatCode(() -> NlsPayloadBuilder.build(merged))
        .as("mapping collision in language '%s'", NlsNamespaces.languageName(language)).doesNotThrowAnyException();
  }

  @ParameterizedTest(name = "language {0}")
  @NullSource
  @ValueSource(strings = "de")
  @DisplayName("Nesting stays within the allow-listed namespaces and one level deep")
  void nestingContractHolds(String language) {
    assertThat(NlsPayloadBuilder.validateNesting(NlsNamespaces.mergedEntries(language).keySet()))
        .as("nesting contract violated in language '%s'", NlsNamespaces.languageName(language)).isEmpty();
  }

  @ParameterizedTest(name = "language {0}")
  @NullSource
  @ValueSource(strings = "de")
  @DisplayName("Both languages deliver exactly the same client keys")
  void bothLanguagesDeliverTheSameClientKeys(String language) {
    // Guards the nested namespaces in particular: a namespace whose leaf set differs per language would make the
    // client read an undefined property in one language only, which stays silent at runtime.
    assertThat(NlsPayloadBuilder.build(NlsNamespaces.mergedEntries(language)).keySet())
        .as("client key set differs between languages")
        .isEqualTo(NlsPayloadBuilder.build(NlsNamespaces.mergedEntries(null)).keySet());
  }
}
