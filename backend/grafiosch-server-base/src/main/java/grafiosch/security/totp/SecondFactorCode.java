package grafiosch.security.totp;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Classifies an entered second factor and creates recovery codes. Input is normalized first - trimmed, spaces and
 * hyphens removed, upper case. Exactly six digits are an authenticator-app code and stay text, so leading zeros survive.
 * Exactly ten characters of the Base32 alphabet {@code A-Z2-7} are a recovery code. Anything else is malformed and is
 * neither hashed nor looked up.
 *
 * @param kind       what the input is
 * @param normalized the normalized input, null when malformed
 */
public record SecondFactorCode(Kind kind, String normalized) {

  /** The two forms a second factor can take, and a malformed input. */
  public enum Kind {
    TOTP, RECOVERY, MALFORMED
  }

  public static final int RECOVERY_CODE_COUNT = 10;
  private static final int RECOVERY_CODE_LENGTH = 10;
  private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
  private static final Pattern TOTP = Pattern.compile("[0-9]{" + TotpGenerator.DIGITS + "}");
  private static final Pattern RECOVERY = Pattern.compile("[A-Z2-7]{" + RECOVERY_CODE_LENGTH + "}");
  private static final SecureRandom RANDOM = new SecureRandom();

  /**
   * Normalizes and classifies an entered code.
   *
   * @param input the code as typed; null or blank counts as absent and must be checked by the caller first
   */
  public static SecondFactorCode parse(String input) {
    String value = input == null ? "" : input.strip().replace(" ", "").replace("-", "").toUpperCase(Locale.ROOT);
    if (TOTP.matcher(value).matches()) {
      return new SecondFactorCode(Kind.TOTP, value);
    } else if (RECOVERY.matcher(value).matches()) {
      return new SecondFactorCode(Kind.RECOVERY, value);
    }
    return new SecondFactorCode(Kind.MALFORMED, null);
  }

  /** Whether the request carried no code at all, which is a question and not a failure. */
  public static boolean isAbsent(String input) {
    return input == null || input.isBlank();
  }

  /** New recovery codes in their display form {@code XXXXX-XXXXX}. */
  public static List<String> newRecoveryCodes() {
    List<String> codes = new ArrayList<>(RECOVERY_CODE_COUNT);
    for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
      StringBuilder code = new StringBuilder(RECOVERY_CODE_LENGTH + 1);
      for (int c = 0; c < RECOVERY_CODE_LENGTH; c++) {
        if (c == RECOVERY_CODE_LENGTH / 2) {
          code.append('-');
        }
        code.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
      }
      codes.add(code.toString());
    }
    return codes;
  }
}
