package grafiosch.nls;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the class-path-contributed mapping descriptor of grafiosch-base is picked up as declared.
 *
 * <p>
 * When only the library is on the class path, the registry must already contain everything the library's own keys need
 * -- the {@code g.} prefix, which the library owns. The application prefix {@code gt.} is deliberately absent here: it
 * is declared by grafioschtrader-common, and since issue #75 no library key carries it, so no library key maps
 * differently depending on whether the application happens to be deployed alongside.
 * </p>
 */
class NlsMappingRegistryTest {

  @Test
  @DisplayName("Descriptor of grafiosch-base declares the expected pass-through prefixes")
  void declaresPassThroughPrefixes() {
    assertThat(NlsMappingRegistry.passThroughPrefixes()).contains("g.", "UDF_");
  }

  @Test
  @DisplayName("The application prefix is not declared by the library")
  void doesNotDeclareApplicationPrefix() {
    assertThat(NlsMappingRegistry.passThroughPrefixes()).doesNotContain("gt.");
  }

  @Test
  @DisplayName("GT_FILTER is the only nested namespace")
  void declaresNestedNamespaces() {
    assertThat(NlsMappingRegistry.nestedNamespaces()).containsExactly("GT_FILTER");
  }

  @Test
  @DisplayName("Library-owned g.* keys keep their dotted form without the application module")
  void libraryOwnedKeysStayDotted() {
    assertThat(NlsKeyMapper.mapToString("g.input.rule.min.violation")).isEqualTo("g.input.rule.min.violation");
  }
}
