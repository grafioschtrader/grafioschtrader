package grafiosch.limit;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import grafiosch.dto.LimitKey;

/**
 * Caches resolved limit values per user and key.
 *
 * <p>
 * It replaces the process-wide mutable {@code MaxDefaultDBValue.dbValue} cache, which was a field on a shared DTO and
 * could only be invalidated through the global parameter save path. This cache is invalidated on every
 * {@code entity_limit} write, for a single user when that user's roles change, and implicitly on a date change so that
 * a {@code valid_until} expiry takes effect without a restart.
 * </p>
 *
 * <p>
 * It is a separate component rather than a field of the resolver so that the repository, which writes, and the
 * resolver, which reads, can both depend on it without a circular bean reference.
 * </p>
 */
@Component
public class EntityLimitCache {

  private final Map<CacheKey, Optional<Integer>> resolved = new ConcurrentHashMap<>();

  private volatile LocalDate builtOn = LocalDate.now();

  /**
   * Returns the cached value or computes and stores it.
   *
   * @param idUser  the acting user, or {@code null} in a background context
   * @param limitKey the key being resolved
   * @param loader   computes the value on a miss
   * @return the resolved limit, empty meaning unlimited
   */
  public Optional<Integer> get(Integer idUser, LimitKey limitKey, Supplier<Optional<Integer>> loader) {
    discardOnDateChange();
    return resolved.computeIfAbsent(new CacheKey(idUser, limitKey.keyId()), _ -> loader.get());
  }

  /** Drops everything. Called on any write to {@code entity_limit}. */
  public void evictAll() {
    resolved.clear();
  }

  /**
   * Drops the entries of one user. Called when a user's roles change, because the role decides which row the resolver
   * picks.
   *
   * @param idUser the user whose entries become stale
   */
  public void evictUser(Integer idUser) {
    resolved.keySet().removeIf(key -> key.idUser() != null && key.idUser().equals(idUser));
  }

  /**
   * Discards the whole cache when the day rolled over, so that a row whose {@code valid_until} has passed stops being
   * honoured without a restart.
   */
  private void discardOnDateChange() {
    LocalDate today = LocalDate.now();
    if (!today.equals(builtOn)) {
      synchronized (this) {
        if (!today.equals(builtOn)) {
          resolved.clear();
          builtOn = today;
        }
      }
    }
  }

  /** Visible for tests: the date the current content was built on. */
  public LocalDate getBuiltOn() {
    return builtOn;
  }

  /** Visible for tests: forces the next access to see a date change. */
  public void setBuiltOnForTest(LocalDate builtOn) {
    this.builtOn = builtOn;
  }

  private record CacheKey(Integer idUser, String keyId) {
  }
}
