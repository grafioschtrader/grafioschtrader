package grafiosch.repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.data.jpa.repository.JpaRepository;

import grafiosch.entities.EntityLimit;
import grafiosch.rest.UpdateCreateJpaRepository;

public interface EntityLimitJpaRepository
    extends JpaRepository<EntityLimit, Integer>, EntityLimitJpaRepositoryCustom, UpdateCreateJpaRepository<EntityLimit> {

  /**
   * Loads every row configured for one limit type and entity name. The remaining key parts and the role/user scope are
   * filtered in the resolver rather than in the query, because they are nullable and a JPQL predicate over nullable
   * parameters would be considerably harder to read than the handful of rows this returns.
   *
   * @param limitType the {@code byte} value of the limit type
   * @param entityName entity or pseudo entity name of the key
   * @return all rows sharing that limit type and entity name, over every key variant and every scope
   */
  List<EntityLimit> findByLimitTypeAndEntityName(byte limitType, String entityName);

  Stream<EntityLimit> findByIdUser(Integer idUser);

  /**
   * Resolves the single row a user may hold for one daily limit key. Backs the upsert guard in
   * {@link EntityLimitJpaRepositoryImpl#saveOnlyAttributes}: when a limit-increase request is approved the frontend
   * sends a row without id, which would insert and collide with an already existing — often expired — row for the same
   * user and key.
   *
   * <p>
   * Restricted to the daily families by the caller, where the key carries neither a relation nor a scope, so
   * {@code (id_user, limit_type, entity_name)} identifies the row unambiguously.
   * </p>
   *
   * @param idUser the user the row belongs to
   * @param limitType the {@code byte} value of the limit type
   * @param entityName entity or pseudo entity name of the key
   * @return the existing row, or empty when the user has none for this key
   */
  Optional<EntityLimit> findByIdUserAndLimitTypeAndEntityName(Integer idUser, byte limitType, String entityName);

  /**
   * Counts the rows that reference a role, so a role can be reported as still in use before it is removed. The foreign
   * key is {@code ON DELETE RESTRICT}, so a delete would otherwise fail with a constraint violation instead of a
   * message.
   *
   * @param idRole the role
   * @return number of rows configured for that role
   */
  long countByIdRole(Integer idRole);
}
