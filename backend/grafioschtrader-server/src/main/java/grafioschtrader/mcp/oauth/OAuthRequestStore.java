package grafioschtrader.mcp.oauth;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import grafiosch.entities.UserAccessToken;
import grafiosch.types.AccessTokenScope;

/**
 * The short-lived state of the authorization flow, held in the memory of this process: validated authorization
 * requests waiting for the user's consent, and issued authorization codes waiting for their exchange. Both are bounded
 * in number and time, and both are taken out on use, so neither can be used twice.
 *
 * <p>
 * A full request store refuses new requests rather than evicting existing ones, so a flood of anonymous validations
 * cannot push a user's pending consent out. Codes are kept by their SHA-256 hash only.
 * </p>
 */
@Component
@ConditionalOnProperty(prefix = "gt.use", name = "mcp", havingValue = "true")
public class OAuthRequestStore {

  public static final int MAX_PENDING_REQUESTS = 500;
  public static final Duration REQUEST_LIFETIME = Duration.ofMinutes(10);
  public static final int MAX_CODES = 1000;
  public static final Duration CODE_LIFETIME = Duration.ofSeconds(60);

  private static final SecureRandom RANDOM = new SecureRandom();

  /** A validated authorization request. */
  public record PendingRequest(String clientId, String clientName, String clientHost, String redirectUri, String state,
      String codeChallenge, AccessTokenScope scope, String resource, long createdNanos) {
  }

  /** An issued authorization code, bound to everything the token request has to repeat. */
  public record CodeGrant(Integer idUser, String clientId, String clientName, String redirectUri, String codeChallenge,
      AccessTokenScope scope, String resource, long createdNanos) {
  }

  private final Map<String, PendingRequest> pending = new LinkedHashMap<>();
  private final Map<String, CodeGrant> codes = new LinkedHashMap<>();

  /**
   * Stores a validated request.
   *
   * @return the id the consent page refers to, or null when the store is full
   */
  public synchronized String putRequest(PendingRequest request) {
    expire(pending, REQUEST_LIFETIME);
    if (pending.size() >= MAX_PENDING_REQUESTS) {
      return null;
    }
    String id = randomToken();
    pending.put(id, request);
    return id;
  }

  /** Returns a pending request without consuming it, for showing the consent page. */
  public synchronized PendingRequest peekRequest(String id) {
    expire(pending, REQUEST_LIFETIME);
    return id == null ? null : pending.get(id);
  }

  /** Takes a pending request out; a consent can be given only once. */
  public synchronized PendingRequest takeRequest(String id) {
    expire(pending, REQUEST_LIFETIME);
    return id == null ? null : pending.remove(id);
  }

  /**
   * Issues a one-time authorization code.
   *
   * @return the code in clear text, or null when too many codes are outstanding
   */
  public synchronized String putCode(CodeGrant grant) {
    expire(codes, CODE_LIFETIME);
    if (codes.size() >= MAX_CODES) {
      return null;
    }
    String code = randomToken();
    codes.put(UserAccessToken.hashToken(code), grant);
    return code;
  }

  /** Redeems a code: it is removed in the same step, so a second exchange finds nothing. */
  public synchronized CodeGrant takeCode(String code) {
    expire(codes, CODE_LIFETIME);
    return code == null ? null : codes.remove(UserAccessToken.hashToken(code));
  }

  synchronized int pendingCount() {
    expire(pending, REQUEST_LIFETIME);
    return pending.size();
  }

  private static void expire(Map<String, ?> map, Duration lifetime) {
    long now = System.nanoTime();
    Iterator<? extends Map.Entry<String, ?>> it = map.entrySet().iterator();
    while (it.hasNext()) {
      Object value = it.next().getValue();
      long created = value instanceof PendingRequest p ? p.createdNanos() : ((CodeGrant) value).createdNanos();
      if (now - created < lifetime.toNanos()) {
        // Insertion order: everything after this entry is younger
        break;
      }
      it.remove();
    }
  }

  static String randomToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
