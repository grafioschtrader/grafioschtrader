package grafiosch.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafiosch.entities.UserEntityChangeCount;
import grafiosch.entities.UserEntityChangeCount.UserEntityChangeCountId;
import grafiosch.entities.projection.UserCountLimit;

public interface UserEntityChangeCountJpaRepository
    extends JpaRepository<UserEntityChangeCount, UserEntityChangeCountId> {

  @Query(nativeQuery = true)
  Optional<UserCountLimit> getCudTransactionAndUserLimit(Integer idUser, String entityName);

}
