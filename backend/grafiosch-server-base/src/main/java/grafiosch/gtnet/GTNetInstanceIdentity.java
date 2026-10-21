package grafiosch.gtnet;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/**
 * Proves that a URL leads back to this running instance, so that GTNet can tell its own entry from the entry of a peer.
 *
 * <p>
 * The address of an entry cannot answer that question. Behind IPv4 NAT, inside a container or behind a reverse proxy,
 * the public name resolves to an address that no network interface of this machine carries, and two instances on one
 * host share every address and differ only in the port. What the URL leads to is instead established by asking it: this
 * instance draws a random challenge, sends it to the URL and accepts the URL as its own only if the answer is the HMAC
 * of that challenge under a key that never leaves this process. A peer that has never seen the key cannot produce the
 * answer.
 * </p>
 *
 * <p>
 * The key is drawn anew at every start and is not stored. A challenge is answered only by the process that is running
 * when it is asked, which is exactly the question, and nothing persistent can leak. A server that merely relays the
 * request to this instance passes the check, because it is then indistinguishable from a reverse proxy — whoever enters
 * such a URL as the own address has chosen to be reached through it.
 * </p>
 */
@Component
public class GTNetInstanceIdentity {

  private static final String HMAC_ALGORITHM = "HmacSHA256";

  /** Bytes of randomness in a challenge; Base64url without padding turns 32 bytes into 43 characters. */
  private static final int CHALLENGE_BYTES = 32;

  /** Accepted shape of an incoming challenge: Base64url, long enough to be unguessable, short enough to be cheap. */
  private static final Pattern CHALLENGE_PATTERN = Pattern.compile("[A-Za-z0-9_-]{32,128}");

  private final SecureRandom secureRandom = new SecureRandom();
  private final SecretKeySpec key;

  public GTNetInstanceIdentity() {
    byte[] keyBytes = new byte[32];
    secureRandom.nextBytes(keyBytes);
    key = new SecretKeySpec(keyBytes, HMAC_ALGORITHM);
  }

  /**
   * Draws a new challenge to be sent to a URL whose identity is in question.
   *
   * @return a Base64url string without padding that satisfies {@link #isWellFormedChallenge(String)}
   */
  public String newChallenge() {
    byte[] challenge = new byte[CHALLENGE_BYTES];
    secureRandom.nextBytes(challenge);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(challenge);
  }

  /**
   * Tells whether an incoming challenge has the shape this class produces. The answering endpoint refuses anything else
   * so that it never computes over arbitrary input.
   *
   * @param challenge the challenge as received, may be null
   * @return true when the challenge may be answered
   */
  public boolean isWellFormedChallenge(String challenge) {
    return challenge != null && CHALLENGE_PATTERN.matcher(challenge).matches();
  }

  /**
   * Answers a challenge with the HMAC under this process's key.
   *
   * @param challenge a challenge that satisfies {@link #isWellFormedChallenge(String)}
   * @return the answer as Base64url without padding
   */
  public String answer(String challenge) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(key);
      return Base64.getUrlEncoder().withoutPadding()
          .encodeToString(mac.doFinal(challenge.getBytes(StandardCharsets.US_ASCII)));
    } catch (GeneralSecurityException e) {
      // HmacSHA256 is mandatory on every Java platform, so this would be a broken runtime rather than bad input.
      throw new IllegalStateException(e);
    }
  }

  /**
   * Checks whether an answer to a challenge of this process was given by this process.
   *
   * @param challenge the challenge this process sent
   * @param answer    the answer the URL returned, may be null
   * @return true when the URL that answered is this running instance
   */
  public boolean isOwnAnswer(String challenge, String answer) {
    return answer != null && MessageDigest.isEqual(answer(challenge).getBytes(StandardCharsets.US_ASCII),
        answer.strip().getBytes(StandardCharsets.US_ASCII));
  }
}
