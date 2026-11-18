package grafiosch.security.totp;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.InstantSource;
import java.util.OptionalLong;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Time-based one-time passwords after RFC 6238 with the parameters every authenticator app supports: HMAC-SHA1, six
 * digits, a step of 30 seconds and a secret of 20 random bytes, shown Base32-encoded without padding. A code is
 * accepted for the current step and the one before and after it, which covers clocks that are off by up to half a
 * minute and a code typed shortly before its step ended.
 *
 * <p>
 * The time comes from an {@link InstantSource}, so tests compute codes for an explicit instant.
 * </p>
 */
@Component
public class TotpGenerator {
  public static final int DIGITS = 6;
  public static final long STEP_SECONDS = 30;
  public static final int SECRET_BYTES = 20;
  private static final String ALGORITHM = "HmacSHA1";
  private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
  private static final SecureRandom RANDOM = new SecureRandom();

  private final InstantSource clock;

  @Autowired
  public TotpGenerator() {
    this(InstantSource.system());
  }

  public TotpGenerator(InstantSource clock) {
    this.clock = clock;
  }

  /** A new random secret, Base32 without padding. */
  public static String newSecret() {
    byte[] secret = new byte[SECRET_BYTES];
    RANDOM.nextBytes(secret);
    return base32Encode(secret);
  }

  /** The time step the clock is in now. */
  public long currentStep() {
    return Math.floorDiv(clock.instant().getEpochSecond(), STEP_SECONDS);
  }

  /**
   * Checks a six-digit code against the steps {@code T-1}, {@code T} and {@code T+1}.
   *
   * @param secretBase32 the secret of the factor
   * @param code         six digits, kept as text so that leading zeros count
   * @return the step the code belongs to, or empty when it matches none of the three
   */
  public OptionalLong matchingStep(String secretBase32, String code) {
    if (code == null || code.length() != DIGITS) {
      return OptionalLong.empty();
    }
    byte[] key = base32Decode(secretBase32);
    byte[] given = code.getBytes(StandardCharsets.US_ASCII);
    long now = currentStep();
    long found = Long.MIN_VALUE;
    // All three steps are computed, whichever matches, so the time does not reveal the position of the match
    for (long step = now - 1; step <= now + 1; step++) {
      if (MessageDigest.isEqual(given, code(key, step).getBytes(StandardCharsets.US_ASCII))) {
        found = step;
      }
    }
    return found == Long.MIN_VALUE ? OptionalLong.empty() : OptionalLong.of(found);
  }

  /**
   * Computes the code of one step (RFC 4226 dynamic truncation, reduced to {@link #DIGITS} digits).
   *
   * @param key  the raw secret
   * @param step the time step
   * @return the code with leading zeros
   */
  public static String code(byte[] key, long step) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(key, ALGORITHM));
      byte[] hash = mac.doFinal(new byte[] { (byte) (step >>> 56), (byte) (step >>> 48), (byte) (step >>> 40),
          (byte) (step >>> 32), (byte) (step >>> 24), (byte) (step >>> 16), (byte) (step >>> 8), (byte) step });
      int offset = hash[hash.length - 1] & 0x0f;
      int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
          | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
      return String.format("%0" + DIGITS + "d", binary % 1_000_000);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(ALGORITHM + " is not available", e);
    }
  }

  /**
   * Builds the {@code otpauth://} URI an authenticator app reads from the QR code.
   *
   * @param issuer       name of the application shown in the app
   * @param account      e-mail address of the user
   * @param secretBase32 the secret
   * @return the URI with issuer and account percent-encoded
   */
  public static String otpauthUri(String issuer, String account, String secretBase32) {
    String encodedIssuer = percentEncode(issuer);
    return "otpauth://totp/" + encodedIssuer + ":" + percentEncode(account) + "?secret=" + secretBase32 + "&issuer="
        + encodedIssuer + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
  }

  private static String percentEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  /** Base32 after RFC 4648 without padding. */
  public static String base32Encode(byte[] data) {
    StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
    int buffer = 0;
    int bits = 0;
    for (byte b : data) {
      buffer = (buffer << 8) | (b & 0xff);
      bits += 8;
      while (bits >= 5) {
        out.append(BASE32[(buffer >>> (bits - 5)) & 0x1f]);
        bits -= 5;
      }
    }
    if (bits > 0) {
      out.append(BASE32[(buffer << (5 - bits)) & 0x1f]);
    }
    return out.toString();
  }

  /**
   * Decodes Base32 after RFC 4648; padding, spaces and lower case are tolerated.
   *
   * @throws IllegalArgumentException for a character outside the alphabet
   */
  public static byte[] base32Decode(String text) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    int buffer = 0;
    int bits = 0;
    for (char c : text.toUpperCase().toCharArray()) {
      if (c == '=' || c == ' ') {
        continue;
      }
      int value = c >= 'A' && c <= 'Z' ? c - 'A' : c >= '2' && c <= '7' ? c - '2' + 26 : -1;
      if (value < 0) {
        throw new IllegalArgumentException("Not a Base32 character");
      }
      buffer = (buffer << 5) | value;
      bits += 5;
      if (bits >= 8) {
        out.write((buffer >>> (bits - 8)) & 0xff);
        bits -= 8;
      }
    }
    return out.toByteArray();
  }
}
