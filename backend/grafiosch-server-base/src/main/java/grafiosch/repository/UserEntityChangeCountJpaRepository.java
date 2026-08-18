package grafiosch.repository;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
