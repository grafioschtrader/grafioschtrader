package grafiosch.rest;

import java.lang.reflect.InvocationTargetException;
import java.util.Date;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;

import grafiosch.GlobalParamKeyBaseDefault;
import grafiosch.common.UserAccessHelper;
import grafiosch.entities.BaseID;
import grafiosch.entities.User;
import grafiosch.entities.UserEntityChangeCount;
import grafiosch.entities.UserEntityChangeCount.UserEntityChangeCountId;
import grafiosch.entities.projection.UserCountLimit;
import grafiosch.error.LimitEntityTransactionError;
import grafiosch.exceptions.LimitEntityTransactionException;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafiosch.repository.UserEntityChangeCountJpaRepository;
import grafiosch.types.OperationType;

/**
 * Abstract class providing logging and daily limit checks for create, update, and delete (CUD) operations on entities.
 * It is intended to be extended by REST controllers that manage entities requiring such controls.
 * <p>
 * This class maintains a count of CUD operations performed by a user on a specific entity type per day.
 * It checks these counts against predefined limits stored in UserEntityChangeLimit.
 * If a limit is exceeded, a {@link LimitEntityTransactionException} is thrown.
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
  private UserEntityChangeCountJpaRepository userEntityChangeCountJpaRepository;

  
  /**
   * Logs an add, update, or delete operation performed by a user on an entity class.
   * This version is used when the entity instance might not be available (e.g., after deletion).
   *
   * @param idUser The ID of the user performing the operation.
   * @param zclass The class of the entity that was affected.
   * @param operationType The type of operation (ADD, UPDATE, DELETE).
   */
  protected void logAddUpdDel(Integer idUser, Class<T> zclass, OperationType operationType) {
    logAddUpdDel(idUser, zclass.getSimpleName(), operationType);
  }

  /**
   * Logs an add, update, or delete operation performed by a user on an entity.
   *
   * @param idUser The ID of the user performing the operation.
   * @param entity The entity that was affected by the operation.
   * @param operationType The type of operation (ADD, UPDATE, DELETE).
   */
  protected void logAddUpdDel(Integer idUser, T entity, OperationType operationType) {
    logAddUpdDel(idUser, entity.getClass().getSimpleName(), operationType);
  }
  
  /**
   * This increases the counter for the corresponding operation by one. 
   * 
   * @param idUser The user performing the operation. Their ID is used to track daily limits.
   * @param entityName The entity instance involved in the CUD operation.
   * @param operationType The type of operation (ADD, UPDATE, DELETE).
   */
  protected void logAddUpdDel(Integer idUser, String entityName, OperationType operationType) {
    UserEntityChangeCount userEntityChangeCount = userEntityChangeCountJpaRepository
        .findById(new UserEntityChangeCountId(idUser, new Date(), entityName))
        .orElse(new UserEntityChangeCount(new UserEntityChangeCountId(idUser, new Date(), entityName)));
    userEntityChangeCount.incrementCounter(operationType);
    userEntityChangeCountJpaRepository.save(userEntityChangeCount);
  }

  /**
   * Checks if the current CUD operation for the given entity by the specified user exceeds the daily limit. If the
   * limit is reached or exceeded, a {@link LimitEntityTransactionException} is thrown. Otherwise, the count for the
   * operation is incremented.
   *
   * @param entity The entity instance involved in the CUD operation. Its class name is used to identify the entity type
   *               for limit checking.
   * @param user   The user performing the operation. Their ID is used to track daily limits.
   * @throws LimitEntityTransactionException If the daily CUD limit for this entity type and user is exceeded.
   * @throws IllegalAccessException          if a property accessor method is not found or is inaccessible during
   *                                         reflection (though this method does not directly use reflection that would
   *                                         throw this for the provided parameters).
   * @throws InvocationTargetException       if a property accessor method throws an exception during reflection (though
   *                                         this method does not directly use reflection that would throw this for the
   *                                         provided parameters).
   * @throws NoSuchMethodException           if a property accessor method is not found during reflection (though this
   *                                         method does not directly use reflection that would throw this for the
   *                                         provided parameters).
   */
  protected void checkDailyLimitOnCRUDOperations(T entity, User user) {
    if (UserAccessHelper.isLimitedEditingUser(user)) {
      String entityName = entity.getClass().getSimpleName();
      Optional<UserCountLimit> userCountLimitOpt = userEntityChangeCountJpaRepository
          .getCudTransactionAndUserLimit(user.getIdUser(), entityName);
      if (userCountLimitOpt.isPresent()) {
        String key = GlobalParamKeyBaseDefault.GT_LIMIT_DAY + entityName;
        Integer limit = userCountLimitOpt.get().getDayLimit() != null ? userCountLimitOpt.get().getDayLimit()
            : globalparametersJpaRepository.getMaxValueByKey(key);
        int cudTransaction = userCountLimitOpt.get().getCudTrans();
        if (cudTransaction >= limit) {
          throw new LimitEntityTransactionException(new LimitEntityTransactionError(entityName, limit, cudTransaction));
        }
      }
    }
  }
}
