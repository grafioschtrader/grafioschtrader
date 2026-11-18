package grafiosch.security.totp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.InstantSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/**
 * Checks the TOTP computation against the SHA-1 test vectors of RFC 6238 appendix B. The RFC lists eight digits; the
 * six-digit code an authenticator app shows is the same truncated value modulo 10^6, i.e. its last six digits.
 */
class TotpGeneratorTest {

  /** The 20-byte ASCII seed of the RFC for HMAC-SHA1. */
  private static final byte[] RFC_SEED = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

  private static TotpGenerator at(long epochSecond) {
    return new TotpGenerator(InstantSource.fixed(Instant.ofEpochSecond(epochSecond)));
  }

  @ParameterizedTest(name = "T = {0}s → {1}")
  @CsvSource({ "59, 287082", "1111111109, 081804", "1111111111, 050471", "1234567890, 005924",
      "2000000000, 279037", "20000000000, 353130" })
  @DisplayName("RFC 6238 SHA-1 vectors reduced to six digits")
  void rfcVectors(long epochSecond, String expected) {
    TotpGenerator generator = at(epochSecond);
    assertEquals(expected, TotpGenerator.code(RFC_SEED, generator.currentStep()));
    String secret = TotpGenerator.base32Encode(RFC_SEED);
    assertEquals(generator.currentStep(), generator.matchingStep(secret, expected).getAsLong());
  }

  @Test
  @DisplayName("A code is accepted one step before and after its own, not two")
  void stepWindow() {
    String secret = TotpGenerator.base32Encode(RFC_SEED);
    long step = at(1234567890).currentStep();
    String code = TotpGenerator.code(RFC_SEED, step);
    long base = step * TotpGenerator.STEP_SECONDS;
    assertEquals(step, at(base - TotpGenerator.STEP_SECONDS).matchingStep(secret, code).getAsLong());
    assertEquals(step, at(base + TotpGenerator.STEP_SECONDS).matchingStep(secret, code).getAsLong());
    assertTrue(at(base - 2 * TotpGenerator.STEP_SECONDS).matchingStep(secret, code).isEmpty());
    assertTrue(at(base + 2 * TotpGenerator.STEP_SECONDS).matchingStep(secret, code).isEmpty());
  }

  @Test
  @DisplayName("Base32 round trip of a random secret; the RFC 4648 vector 'foobar'")
  void base32() {
    assertEquals("MZXW6YTBOI", TotpGenerator.base32Encode("foobar".getBytes(StandardCharsets.US_ASCII)));
    String secret = TotpGenerator.newSecret();
    assertEquals(32, secret.length());
    assertArrayEquals(TotpGenerator.base32Decode(secret),
        TotpGenerator.base32Decode(TotpGenerator.base32Encode(TotpGenerator.base32Decode(secret))));
  }

  @Test
  @DisplayName("Entered codes are normalized and classified; recovery codes have the display form XXXXX-XXXXX")
  void codeClassification() {
    assertEquals(new SecondFactorCode(SecondFactorCode.Kind.TOTP, "012345"), SecondFactorCode.parse(" 012 345 "));
    assertEquals(SecondFactorCode.Kind.RECOVERY, SecondFactorCode.parse("abcde-fg234").kind());
    assertEquals("ABCDEFG234", SecondFactorCode.parse("abcde-fg234").normalized());
    assertEquals(SecondFactorCode.Kind.MALFORMED, SecondFactorCode.parse("12345").kind());
    assertEquals(SecondFactorCode.Kind.MALFORMED, SecondFactorCode.parse("ABCDE-FG189").kind());
    for (String code : SecondFactorCode.newRecoveryCodes()) {
      assertTrue(code.matches("[A-Z2-7]{5}-[A-Z2-7]{5}"), code);
      assertEquals(SecondFactorCode.Kind.RECOVERY, SecondFactorCode.parse(code).kind());
    }
  }
}
