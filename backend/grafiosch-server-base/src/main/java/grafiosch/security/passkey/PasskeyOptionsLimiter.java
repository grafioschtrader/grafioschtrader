package grafiosch.security.passkey;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

/**
 * Request budget of the public passkey options endpoint: 10 requests per minute and client address. Every request
 * creates a challenge entry, so the budget keeps one address from filling the store. The buckets are bounded in number
 * and forgotten after a quiet minute; a full map refuses a new address.
 */
@Component
public class PasskeyOptionsLimiter {
  private static final long LIFETIME = Duration.ofMinutes(1).toNanos();
  private static final int CAPACITY = 10_000;
  private static final int REQUESTS_PER_MINUTE = 10;
  private final Map<String, Entry> entries = new HashMap<>();

  private record Entry(Bucket bucket, long lastSeen) {
  }

  public synchronized boolean tryConsume(String address) {
    long now = System.nanoTime();
    entries.values().removeIf(entry -> now - entry.lastSeen() >= LIFETIME);
    Entry entry = entries.get(address);
    if (entry == null) {
      if (entries.size() >= CAPACITY) {
        return false;
      }
      entry = new Entry(Bucket.builder().addLimit(Bandwidth.builder().capacity(REQUESTS_PER_MINUTE)
          .refillGreedy(REQUESTS_PER_MINUTE, Duration.ofMinutes(1)).build()).build(), now);
    }
    entries.put(address, new Entry(entry.bucket(), now));
    return entry.bucket().tryConsume(1);
  }
}
