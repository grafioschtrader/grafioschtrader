package grafiosch.security.passkey;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.stereotype.Component;

/**
 * The issued WebAuthn challenges, held in the memory of this process because the application is stateless and keeps no
 * HTTP session. An entry lives five minutes, at most 500 exist, and a full store refuses new entries instead of evicting
 * a pending one. {@link #take(String)} removes an entry and checks its age in one synchronized step, so a challenge can
 * be answered once. Everything the answer is checked against comes from the entry, never from the client.
 */
@Component
public class PasskeyChallengeStore {
  public static final Duration LIFETIME = Duration.ofMinutes(5);
  public static final int MAX_ENTRIES = 500;
  private static final SecureRandom RANDOM = new SecureRandom();

  /** What a challenge was issued for. */
  public enum Purpose {
    REGISTRATION, SIGN_IN
  }

  /**
   * One issued challenge.
   *
   * @param purpose         registration or sign-in
   * @param creationOptions the options of a registration exactly as issued, else null
   * @param requestOptions  the options of a sign-in exactly as issued, else null
   * @param publicBaseUrl   the value of {@code g.public.base.url} the options were built for
   * @param idUser          the user of a registration, else null
   * @param authVersion     the authentication version of that user at the time of the proof
   * @param createdNanos    {@link System#nanoTime()} at issue
   */
  public record Entry(Purpose purpose, PublicKeyCredentialCreationOptions creationOptions,
      PublicKeyCredentialRequestOptions requestOptions, String publicBaseUrl, Integer idUser, int authVersion,
      long createdNanos) {
  }

  private final Map<String, Entry> entries = new LinkedHashMap<>();

  /** Stores the challenge of a registration; returns its id, or null when the store is full. */
  public String putRegistration(PublicKeyCredentialCreationOptions options, String publicBaseUrl, Integer idUser,
      int authVersion) {
    return put(new Entry(Purpose.REGISTRATION, options, null, publicBaseUrl, idUser, authVersion, System.nanoTime()));
  }

  /** Stores the challenge of a sign-in; returns its id, or null when the store is full. */
  public String putSignIn(PublicKeyCredentialRequestOptions options, String publicBaseUrl) {
    return put(new Entry(Purpose.SIGN_IN, null, options, publicBaseUrl, null, 0, System.nanoTime()));
  }

  /** Takes an entry out; null for an unknown, expired or already answered challenge. */
  public synchronized Entry take(String id) {
    expire();
    return id == null ? null : entries.remove(id);
  }

  private synchronized String put(Entry entry) {
    expire();
    if (entries.size() >= MAX_ENTRIES) {
      return null;
    }
    byte[] bytes = new byte[24];
    RANDOM.nextBytes(bytes);
    String id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    entries.put(id, entry);
    return id;
  }

  private void expire() {
    long now = System.nanoTime();
    Iterator<Entry> it = entries.values().iterator();
    while (it.hasNext()) {
      if (now - it.next().createdNanos() < LIFETIME.toNanos()) {
        // Insertion order: everything after this entry is younger
        break;
      }
      it.remove();
    }
  }
}
