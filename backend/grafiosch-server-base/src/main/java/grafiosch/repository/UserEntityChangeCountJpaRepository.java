package grafiosch.repository;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.UserEntityChangeCount;
import grafiosch.entities.UserEntityChangeCount.UserEntityChangeCountId;

/**
 * Repository interface for managing user entity change counts, the runtime counters behind the daily limit types.
 */
public interface UserEntityChangeCountJpaRepository
    extends JpaRepository<UserEntityChangeCount, UserEntityChangeCountId> {

  /**
   * Returns how many create, update and delete operations the user has already performed today on one entity or pseudo
   * entity.
   *
   * <p>
   * A missing counter row counts as 0. That matters: the query this replaced was driven by the counter table and
   * returned nothing when no row existed, so the daily check was skipped entirely and the first operation of every day
   * was free — a first-of-day bulk operation was unbounded.
   * </p>
   *
   * @param idUser     the user whose operations are counted
   * @param entityName entity or pseudo entity name the counter is kept under
   * @return today's operation count, 0 when the user has not operated on this entity today
   */
  default int getCudTransactionCount(Integer idUser, String entityName) {
    return findById(new UserEntityChangeCountId(idUser, LocalDate.now(), entityName))
        .map(count -> count.getCountInsert() + count.getCountUpdate() + count.getCountDelete()).orElse(0);
  }

  /**
   * Creates the counter row of a day with all three counters at 0, unless it exists already. Concurrent first calls of
   * a day collide on the primary key, and exactly one of them creates the row. Together with
   * {@link #incrementInsertBelowLimit} it reserves one unit of a daily budget without a read-then-write race.
   *
   * Named query: UserEntityChangeCount.insertIgnoreZeroCount
   *
   * @param idUser     the user the counter belongs to
   * @param date       the day of the counter, the UTC day for server policies
   * @param entityName entity or pseudo entity name the counter is kept under
   * @return 1 when the row was created, 0 when it existed already
   */
  @Transactional
  @Modifying
  @Query(nativeQuery = true)
  int insertIgnoreZeroCount(Integer idUser, LocalDate date, String entityName);

  /**
   * Raises the insert counter by one, but only while it is below the limit. The condition is decided under the row lock
   * of the update, so among concurrent calls at the last free unit exactly one succeeds and the counter never exceeds
   * the limit.
   *
   * Named query: UserEntityChangeCount.incrementInsertBelowLimit
   *
   * @param idUser     the user the counter belongs to
   * @param date       the day of the counter
   * @param entityName entity or pseudo entity name the counter is kept under
   * @param limit      the daily budget; the counter is raised only while it is smaller
   * @return 1 when a unit was reserved, 0 when the budget is exhausted
   */
  @Transactional
  @Modifying
  @Query(nativeQuery = true)
  int incrementInsertBelowLimit(Integer idUser, LocalDate date, String entityName, int limit);
}
