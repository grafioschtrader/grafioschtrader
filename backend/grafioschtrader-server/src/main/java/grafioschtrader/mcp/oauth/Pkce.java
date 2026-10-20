package grafioschtrader.mcp.oauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Proof Key for Code Exchange (RFC 7636) with the method S256, the only one GT accepts. A verifier consists of 43 to
 * 128 unreserved characters; the challenge is the base64url encoded SHA-256 of the verifier.
 */
public final class Pkce {

  private static final Pattern VERIFIER = Pattern.compile("[A-Za-z0-9\\-._~]{43,128}");
  /** base64url without padding of a SHA-256 digest is always 43 characters. */
  private static final Pattern CHALLENGE = Pattern.compile("[A-Za-z0-9\\-_]{43}");

  private Pkce() {
  }

  public static boolean isValidChallenge(String challenge) {
    return challenge != null && CHALLENGE.matcher(challenge).matches();
  }

  /**
   * Checks a verifier against the challenge of the authorization request, in constant time.
   *
   * @return true when the verifier is well formed and its S256 transformation equals the challenge
   */
  public static boolean verify(String verifier, String challenge) {
    if (verifier == null || challenge == null || !VERIFIER.matcher(verifier).matches()) {
      return false;
    }
    try {
      String computed = Base64.getUrlEncoder().withoutPadding()
          .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
      return MessageDigest.isEqual(computed.getBytes(StandardCharsets.US_ASCII),
          challenge.getBytes(StandardCharsets.US_ASCII));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
