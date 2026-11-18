package grafiosch.service;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * Counts wrong proofs per user: wrong or malformed second-factor codes at sign-in, and wrong passwords or codes given
 * to an endpoint that manages sign-in methods. Five wrong proofs of one user within 15 minutes refuse every further
 * proof of that user until 15 minutes after the fifth; a correct proof clears the count.
 *
 * <p>
 * The lock is per user and leaves the client address alone, so the holder of a stolen password cannot lock other users
 * out of their address, and the holder of a session cannot search the password at a management endpoint without being
 * stopped. A missing code is a question, and a secret that cannot be decrypted is an operating fault; neither is
 * counted. The counters live in the memory of the one JVM serving the database, bounded in number: the least recently
 * touched entry gives way when the capacity is reached.
 * </p>
 */
@Service
public class ProofAttemptService {
  public static final int MAX_WRONG_PROOFS = 5;
  public static final Duration WINDOW = Duration.ofMinutes(15);
  static final int CAPACITY = 10_000;

  /** Times of the recent wrong proofs of one user, and the end of a lock in force. */
  private static final class Attempts {
    private final Deque<Long> failures = new ArrayDeque<>();
    /** System.nanoTime() at which the lock ends; null while none is in force. */
    private Long lockedUntil;
  }

  private final Map<Integer, Attempts> attempts = new LinkedHashMap<>(16, 0.75f, true) {
    private static final long serialVersionUID = 1L;

    @Override
    protected boolean removeEldestEntry(Map.Entry<Integer, Attempts> eldest) {
      return size() > CAPACITY;
    }
  };

  /** Whether proofs of this user are refused at the moment; nothing is checked while it is. */
  public synchronized boolean isLocked(Integer idUser) {
    Attempts entry = attempts.get(idUser);
    return entry != null && entry.lockedUntil != null && entry.lockedUntil - System.nanoTime() > 0;
  }

  /** Records a wrong proof; the fifth within the window starts the lock. */
  public synchronized void failed(Integer idUser) {
    long now = System.nanoTime();
    Attempts entry = attempts.computeIfAbsent(idUser, _ -> new Attempts());
    while (!entry.failures.isEmpty() && now - entry.failures.peekFirst() >= WINDOW.toNanos()) {
      entry.failures.pollFirst();
    }
    entry.failures.addLast(now);
    if (entry.failures.size() >= MAX_WRONG_PROOFS) {
      entry.lockedUntil = now + WINDOW.toNanos();
      entry.failures.clear();
    }
  }

  /** A correct proof clears the count of the user. */
  public synchronized void succeeded(Integer idUser) {
    attempts.remove(idUser);
  }
}
