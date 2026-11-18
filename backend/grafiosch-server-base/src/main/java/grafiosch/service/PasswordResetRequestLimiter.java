package grafiosch.service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

/** Bounded per-address buckets. A full store refuses new addresses until an existing entry expires. */
@Service
public class PasswordResetRequestLimiter {
  private static final long LIFETIME = Duration.ofHours(1).toNanos();
  private static final int CAPACITY = 10000;
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
      entry = new Entry(Bucket.builder()
          .addLimit(Bandwidth.builder().capacity(5).refillGreedy(5, Duration.ofHours(1)).build()).build(), now);
    }
    entries.put(address, new Entry(entry.bucket(), now));
    return entry.bucket().tryConsume(1);
  }
}
