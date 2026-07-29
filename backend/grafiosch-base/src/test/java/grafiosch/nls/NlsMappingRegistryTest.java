package grafiosch.nls;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the class-path-contributed mapping descriptor of grafiosch-base is picked up as declared.
 *
 * <p>
 * When only the library is on the class path, the registry must already contain everything the library's own keys need
 * -- in particular the {@code gt.} prefix, because grafiosch-base still ships {@code gt.*} keys and their mapping must
 * not depend on whether the grafioschtrader application happens to be deployed alongside.
 * </p>
 */
class NlsMappingRegistryTest {

  @Test
  @DisplayName("Descriptor of grafiosch-base declares the expected pass-through prefixes")
  void declaresPassThroughPrefixes() {
    assertThat(NlsMappingRegistry.passThroughPrefixes()).contains("g.", "gt.", "UDF_");
  }

  @Test
  @DisplayName("GT_FILTER is the only nested namespace")
  void declaresNestedNamespaces() {
    assertThat(NlsMappingRegistry.nestedNamespaces()).containsExactly("GT_FILTER");
  }

  @Test
  @DisplayName("Library-owned gt.* keys keep their dotted form without the application module")
  void libraryOwnedGtKeysStayDotted() {
    assertThat(NlsKeyMapper.mapToString("gt.input.rule.min.violation")).isEqualTo("gt.input.rule.min.violation");
  }
}
