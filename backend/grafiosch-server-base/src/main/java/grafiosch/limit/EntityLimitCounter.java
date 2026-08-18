package grafiosch.limit;

import grafiosch.dto.LimitKey;
import grafiosch.entities.User;
import jakarta.persistence.EntityManager;

/**
 * Answers "how many rows exist already" for one {@link grafiosch.types.LimitType#MAX} limit key.
 *
 * <p>
 * Resolving the configured number and counting what exists are separate jobs: the resolver reads {@code entity_limit},
 * the counter reads the guarded table. A counter is registered together with its key, because the query depends on the
 * key's {@code ownerScope} and on whether the key guards a flat table or the elements of a nested collection.
 * </p>
 *
 * <p>
 * The counters in {@link LimitCounters} cover the flat cases. Anything that needs a join or a non-standard owner
 * column is supplied as a lambda at registration time.
 * </p>
 */
@FunctionalInterface
public interface EntityLimitCounter {

  /**
   * Counts the rows that already consume the limit.
   *
   * @param entityManager entity manager of the current persistence context
   * @param user          the acting user, or {@code null} in a background context that has no authenticated user. A
   *                      counter whose owner scope needs a user must return 0 in that case rather than fail
   * @param limitKey      the key being checked, so one counter implementation can serve several keys
   * @param parentId      the parent the new element goes into; required for {@code countScope = SINGLE} and
   *                      {@code null} otherwise
   * @return the number of existing rows counted against the limit
   */
  int count(EntityManager entityManager, User user, LimitKey limitKey, Integer parentId);
}
