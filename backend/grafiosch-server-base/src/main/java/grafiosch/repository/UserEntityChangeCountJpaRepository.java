package grafiosch.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafiosch.entities.UserEntityChangeCount;
import grafiosch.entities.UserEntityChangeCount.UserEntityChangeCountId;
import grafiosch.entities.projection.UserCountLimit;

/**
 * Repository interface for managing user entity change counts. Provides methods for retrieving change count and user
 * limit information.
 */
public interface UserEntityChangeCountJpaRepository
    extends JpaRepository<UserEntityChangeCount, UserEntityChangeCountId> {

  /**
   * Returns the total number of insert, update, and delete operations performed by the user for a specific entity on
   * the current date, along with the daily operation limit for the user.
   * <p>
   *
   * @param idUser     the unique identifier of the user
   * @param entityName the name of the entity
   * @return an Optional containing the user's transaction count and daily limit, if available
   */
  @Query(nativeQuery = true)
  Optional<UserCountLimit> getCudTransactionAndUserLimit(Integer idUser, String entityName);

}
