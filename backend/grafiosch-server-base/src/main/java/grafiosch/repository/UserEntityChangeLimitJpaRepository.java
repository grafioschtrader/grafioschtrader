package grafiosch.repository;

import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.data.jpa.repository.JpaRepository;

import grafiosch.entities.UserEntityChangeLimit;
import grafiosch.rest.UpdateCreateJpaRepository;

public interface UserEntityChangeLimitJpaRepository extends JpaRepository<UserEntityChangeLimit, Integer>,
    UserEntityChangeLimitJpaRepositoryCustom, UpdateCreateJpaRepository<UserEntityChangeLimit> {

  Stream<UserEntityChangeLimit> findByIdUser(Integer idUser);

  /**
   * Resolves the single limit a user may hold for a given entity type. Backs the upsert guard in
   * {@link UserEntityChangeLimitJpaRepositoryImpl#saveOnlyAttributes} so an approval for an entity that already has a
   * (possibly expired) limit updates that row instead of inserting a duplicate that would violate the unique key
   * {@code Uecl_unique (id_user, entity_name)}.
   *
   * @param idUser     the user the limit belongs to
   * @param entityName the simple entity class name (or pseudo entity name) the limit applies to
   * @return the existing limit, or empty if the user has none for this entity
   */
  Optional<UserEntityChangeLimit> findByIdUserAndEntityName(Integer idUser, String entityName);

}
