package grafiosch.rest;

import org.springframework.beans.factory.annotation.Autowired;

import grafiosch.entities.BaseID;
import grafiosch.entities.EntityLimit;
import grafiosch.entities.User;
import grafiosch.exceptions.LimitEntityTransactionException;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafiosch.service.DailyLimitService;
import grafiosch.service.EntityLimitService;
import grafiosch.types.OperationType;

/**
 * Abstract class providing logging and daily limit checks for create, update, and delete (CUD) operations on entities.
 * It is intended to be extended by REST controllers that manage entities requiring such controls.
 * <p>
 * This class maintains a count of CUD operations performed by a user on a specific entity type per day. It checks these
 * counts against the {@code DAY_CUD} limit configured in {@link EntityLimit}. If a limit is exceeded, a
 * {@link LimitEntityTransactionException} is thrown.
 * </p>
 * <p>
 * Logging of CUD operations is also handled, typically recording the user, entity type, and operation type.
 * </p>
 *
 * @param <T> The type of the entity being managed, which must extend {@link BaseID}.
 */
public abstract class DailyLimitUpdCreateLogger<T extends BaseID<Integer>> {

  @Autowired
  protected GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  protected DailyLimitService dailyLimitService;

  @Autowired
  protected EntityLimitService entityLimitService;

  /**
   * Logs an add, update, or delete operation performed by a user on an entity class. This version is used when the
   * entity instance might not be available (e.g., after deletion).
   *
   * @param idUser        The ID of the user performing the operation.
   * @param zclass        The class of the entity that was affected.
   * @param operationType The type of operation (ADD, UPDATE, DELETE).
   */
  protected void logAddUpdDel(Integer idUser, Class<T> zclass, OperationType operationType) {
    logAddUpdDel(idUser, zclass.getSimpleName(), operationType);
  }

  /**
   * Logs an add, update, or delete operation performed by a user on an entity.
   *
   * @param idUser        The ID of the user performing the operation.
   * @param entity        The entity that was affected by the operation.
   * @param operationType The type of operation (ADD, UPDATE, DELETE).
   */
  protected void logAddUpdDel(Integer idUser, T entity, OperationType operationType) {
    logAddUpdDel(idUser, entity.getClass().getSimpleName(), operationType);
  }

  /**
   * This increases the counter for the corresponding operation by one.
   *
   * @param idUser        The user performing the operation. Their ID is used to track daily limits.
   * @param entityName    The entity instance involved in the CUD operation.
   * @param operationType The type of operation (ADD, UPDATE, DELETE).
   */
  protected void logAddUpdDel(Integer idUser, String entityName, OperationType operationType) {
    dailyLimitService.log(idUser, entityName, operationType, 1);
  }

  /**
   * Checks if the current CUD operation for the given entity by the specified user exceeds the daily limit. If the
   * limit is reached or exceeded, a {@link LimitEntityTransactionException} is thrown.
   *
   * <p>
   * Which users this applies to is now entirely a matter of configuration: the resolver picks a row written for the
   * user, then one written for the user's most privileged role, then the default row of the key. There is no
   * hardcoded restriction to {@code ROLE_LIMITEDIT} any more, so an administrator can bound an {@code ALLEDIT} or
   * {@code ADMIN} account as well. Out of the box only {@code ROLE_LIMITEDIT} rows are seeded, which keeps the
   * effective behaviour identical until such a row is added.
   * </p>
   *
   * <p>
   * The limit is resolved independently of the counter row and a missing counter row counts as 0. Previously the
   * lookup was driven by the counter table, so the check was skipped entirely until the user's first operation of the
   * day had been recorded.
   * </p>
   *
   * @param entity The entity instance involved in the CUD operation. Its class name is used to identify the entity type
   *               for limit checking.
   * @param user   The user performing the operation. Their ID is used to track daily limits.
   * @throws LimitEntityTransactionException If the daily CUD limit for this entity type and user is exceeded.
   */
  protected void checkDailyLimitOnCRUDOperations(T entity, User user) {
    checkDailyLimitOnCRUDOperations(user, entity.getClass().getSimpleName(), 1);
  }

  /**
   * Checks the daily limit for an entity addressed by name rather than by instance, for the number of operations the
   * caller is about to perform.
   *
   * <p>
   * Used where the budget of one entity is consumed by an operation on another — a bulk price upload counts as one edit
   * of its parent instrument — and where a single request performs several operations at once.
   * </p>
   *
   * @param user       The user performing the operation. Their ID is used to track daily limits.
   * @param entityName Entity or pseudo entity name the limit is configured for.
   * @param additional How many operations this request performs; a value below one is treated as one.
   * @throws LimitEntityTransactionException If the daily CUD limit for this entity name and user would be exceeded.
   */
  protected void checkDailyLimitOnCRUDOperations(User user, String entityName, int additional) {
    dailyLimitService.check(user, entityName, additional);
  }
}
