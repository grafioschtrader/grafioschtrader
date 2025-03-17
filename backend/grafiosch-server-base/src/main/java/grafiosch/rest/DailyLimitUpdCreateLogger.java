package grafiosch.rest;

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
 * Users with a limit can only create or change a certain number of entities of
 * an information class or its shared entities per day. This is used to update
 * the daily counters of an information class or to check its limit.
 * 
 * @param <T>
 */
public abstract class DailyLimitUpdCreateLogger<T extends BaseID> {

  @Autowired
  protected GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  private UserEntityChangeCountJpaRepository userEntityChangeCountJpaRepository;

  protected void logAddUpdDel(Integer idUser, Class<T> zclass, OperationType operationType) {
    logAddUpdDel(idUser, zclass.getSimpleName(), operationType);
  }

  protected void logAddUpdDel(Integer idUser, T entity, OperationType operationType) {
    logAddUpdDel(idUser, entity.getClass().getSimpleName(), operationType);
  }
  
  /**
   * This increases the counter for the corresponding operation by one. 
   * 
   * @param idUser
   * @param entityName
   * @param operationType
   */
  protected void logAddUpdDel(Integer idUser, String entityName, OperationType operationType) {
    UserEntityChangeCount userEntityChangeCount = userEntityChangeCountJpaRepository
        .findById(new UserEntityChangeCountId(idUser, new Date(), entityName))
        .orElse(new UserEntityChangeCount(new UserEntityChangeCountId(idUser, new Date(), entityName)));
    userEntityChangeCount.incrementCounter(operationType);
    userEntityChangeCountJpaRepository.save(userEntityChangeCount);
  }

  /**
   * There is a limit of changes for a user on own public data. This limit is
   * checked here. Only limit user is checked against limit violations.
   *
   * @param entity
   * @param user
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
