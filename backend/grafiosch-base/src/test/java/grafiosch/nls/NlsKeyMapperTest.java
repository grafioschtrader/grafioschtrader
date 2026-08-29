package grafiosch.nls;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Pins the ordered key-mapping rules of {@link NlsKeyMapper}, which are the contract that lets one properties entry
 * serve both the server-side {@code MessageSource} lookup and the client-side {@code TranslateService} lookup.
 */
class NlsKeyMapperTest {

  static Stream<Arguments> mappings() {
    return Stream.of(
        // Rule 4 - the dotted field labels the backend resolves for validation errors.
        Arguments.of("readable.name", "READABLE_NAME"), Arguments.of("rate.limit.type", "RATE_LIMIT_TYPE"),
        // Rule 4 - field labels that are a single lower-case word. A rule keyed off "contains a dot" breaks these.
        Arguments.of("name", "NAME"), Arguments.of("email", "EMAIL"), Arguments.of("isin", "ISIN"),
        Arguments.of("cryptocurrency", "CRYPTOCURRENCY"),
        // Rule 4 - camelCase field labels, including the one that collides in shape with a client validator key.
        Arguments.of("webUrl", "WEBURL"), Arguments.of("close_Tooltip", "CLOSE_TOOLTIP"),
        // Rule 4 is idempotent, which is what allows an UPPER_SNAKE frontend key to be moved over unchanged.
        Arguments.of("GT_NET_FIRST_HANDSHAKE_SEL_RR_S", "GT_NET_FIRST_HANDSHAKE_SEL_RR_S"),
        Arguments.of("REAL_ESTATE_FUND", "REAL_ESTATE_FUND"),
        // Rule 1 - client-only keys keep their exact shape, however unusual.
        Arguments.of("c.required", "required"), Arguments.of("c.webUrl", "webUrl"),
        Arguments.of("c.patternEmail", "patternEmail"), Arguments.of("c.login.failure", "login.failure"),
        Arguments.of("c.login.ipaddress.locked", "login.ipaddress.locked"),
        // Rule 2 - configuration and metadata prefixes pass through untouched.
        Arguments.of("g.jwt.expiration.minutes", "g.jwt.expiration.minutes"), Arguments.of("g.net", "g.net"),
        Arguments.of("g.input.rule.min.violation", "g.input.rule.min.violation"),
        Arguments.of("UDF_URLString", "UDF_URLString"), Arguments.of("UDF_DateTimeNumeric", "UDF_DateTimeNumeric"));
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @MethodSource("mappings")
  @DisplayName("Raw property key maps to the documented client key")
  void mapsRawKeyToClientKey(String rawKey, String expectedClientKey) {
    assertThat(NlsKeyMapper.mapToString(rawKey)).isEqualTo(expectedClientKey);
  }

  @Test
  @DisplayName("Rule 3 splits an allow-listed namespace on the first dot only")
  void nestsAllowListedNamespace() {
    assertThat(NlsKeyMapper.map("GT_FILTER.gtIS")).isEqualTo(new ClientKey.Nested("GT_FILTER", "gtIS"));
    assertThat(NlsKeyMapper.mapToString("GT_FILTER.gtSameOrBefore")).isEqualTo("GT_FILTER.gtSameOrBefore");
  }

  @Test
  @DisplayName("A namespace that is not allow-listed stays flat")
  void doesNotNestUnknownNamespace() {
    assertThat(NlsKeyMapper.map("algo.alarm.stop.loss")).isEqualTo(new ClientKey.Flat("ALGO_ALARM_STOP_LOSS"));
  }

  @Test
  @DisplayName("Rule 1 wins over rule 2, so a client key is never mistaken for a configuration key")
  void clientPrefixTakesPrecedenceOverPassThroughPrefix() {
    assertThat(NlsKeyMapper.mapToString("c.g.someClientKey")).isEqualTo("g.someClientKey");
  }

  @Test
  @DisplayName("Upper-casing is locale independent")
  void upperCasesIndependentlyOfDefaultLocale() {
    Locale previous = Locale.getDefault();
    try {
      // In Turkish, the locale-sensitive toUpperCase() maps 'i' to 'İ', which would corrupt every key containing an i.
      Locale.setDefault(Locale.forLanguageTag("tr"));
      assertThat(NlsKeyMapper.mapToString("isin")).isEqualTo("ISIN");
      assertThat(NlsKeyMapper.mapToString("nickname")).isEqualTo("NICKNAME");
      assertThat(NlsKeyMapper.mapToString("id.user.redirect")).isEqualTo("ID_USER_REDIRECT");
    } finally {
      Locale.setDefault(previous);
    }
  }
}
