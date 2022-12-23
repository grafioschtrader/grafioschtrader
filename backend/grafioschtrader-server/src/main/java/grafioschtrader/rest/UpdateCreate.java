package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.lang.reflect.InvocationTargetException;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DataHelper;
import grafioschtrader.common.PropertyAlwaysUpdatable;
import grafioschtrader.common.PropertySelectiveUpdatableOrWhenNull;
import grafioschtrader.common.UserAccessHelper;
import grafioschtrader.entities.Auditable;
import grafioschtrader.entities.BaseID;
import grafioschtrader.entities.Globalparameters;
import grafioschtrader.entities.ProposeChangeEntity;
import grafioschtrader.entities.ProposeChangeField;
import grafioschtrader.entities.ProposeRequest;
import grafioschtrader.entities.ProposeTransientTransfer;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.TenantBaseID;
import grafioschtrader.entities.User;
import grafioschtrader.entities.UserEntityChangeCount;
import grafioschtrader.entities.UserEntityChangeCount.UserEntityChangeCountId;
import grafioschtrader.entities.UserEntityChangeLimit;
import grafioschtrader.entities.projection.UserCountLimit;
import grafioschtrader.error.LimitEntityTransactionError;
import grafioschtrader.exceptions.LimitEntityTransactionException;
import grafioschtrader.repository.GlobalparametersJpaRepository;
import grafioschtrader.repository.ProposeChangeEntityJpaRepository;
import grafioschtrader.repository.ProposeChangeFieldJpaRepository;
import grafioschtrader.repository.TenantLimitsHelper;
import grafioschtrader.repository.UserEntityChangeCountJpaRepository;
import grafioschtrader.types.OperationType;
import grafioschtrader.types.ProposeDataChangeState;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.Valid;

public abstract class UpdateCreate<T extends BaseID> {

  @PersistenceContext
  private EntityManager entityManager;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  private UserEntityChangeCountJpaRepository userEntityChangeCountJpaRepository;

  @Autowired
  private ProposeChangeFieldJpaRepository proposeChangeFieldJpaRepository;

  @Autowired
  protected ProposeChangeEntityJpaRepository proposeChangeEntityJpaRepository;

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  protected abstract UpdateCreateJpaRepository<T> getUpdateCreateJpaRepository();

  /**
   * Request for a new entity. The new URI-location of the created record is not
   * returned.
   */
  @PostMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<T> create(@Valid @RequestBody T entity) throws Exception {
    return createEntity(entity);
  }

  /**
   * Request for update entity.
   */
  @PutMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<T> update(@Valid @RequestBody final T entity) throws Exception {
    return updateEntity(entity);
  }

  /**
   * Create entity
   *
   * @param entity
   * @return
   * @throws Exception
   */
  protected ResponseEntity<T> createEntity(T entity) throws Exception {
    log.debug("Create Entity : {}", entity);
    DataHelper.setEmptyStringToNullOrRemoveTraillingSpaces(entity);
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    if (entity.getId() != null) {
      return ResponseEntity.badRequest().header("Failure", "Cannot create Entity with existing ID").body(null);
    }

    if (entity instanceof TenantBaseID) {
      if (entity instanceof Tenant) {
        // User can have only one Tenant

        if (user.getIdTenant() != null) {
          throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
        }
      } else {
        entity = checkAndSetEntityWithTenant(entity, user);
      }
      if (!TenantLimitsHelper.canAddWhenCheckedAgainstMayBeExistingTenantLimit(globalparametersJpaRepository, entity)) {
        throw new SecurityException(GlobalConstants.LIMIT_SECURITY_BREACH);
      }
    } else {
      checkDailyLimitAuditable(entity, user);
    }
    final T result = getUpdateCreateJpaRepository().saveOnlyAttributes(entity, null,
        Set.of(PropertySelectiveUpdatableOrWhenNull.class, PropertyAlwaysUpdatable.class));

    logAddUpdDel(user.getIdUser(), result, OperationType.ADD);
    if (entity instanceof UserEntityChangeLimit && ((UserEntityChangeLimit) entity).getIdProposeRequest() != null) {
      // UserEntityChangeLimit can have a proposal request without an existing
      // UserEntityChangeLimit, because the
      // user which caused it, product only a proposal on no entity
      updateEntity(entity);
    }
    return ResponseEntity.ok().body(result);
  }

  /**
   * There is a limit of changes for a user on own public data. This limit is
   * checked here. Only limit user is checked against limit violations.
   *
   * @param entity
   * @param user
   */
  private void checkDailyLimitAuditable(T entity, User user) {
    if (UserAccessHelper.isLimitedEditingUser(user)) {
      String entityName = entity.getClass().getSimpleName();
      Optional<UserCountLimit> userCountLimitOpt = userEntityChangeCountJpaRepository
          .getCudTransactionAndUserLimit(user.getIdUser(), entityName);
      if (userCountLimitOpt.isPresent()) {
        String key = Globalparameters.GT_LIMIT_DAY + entityName;
        Integer limit = userCountLimitOpt.get().getDayLimit() != null ? userCountLimitOpt.get().getDayLimit()
            : globalparametersJpaRepository.getMaxValueByKey(key);
        int cudTransaction = userCountLimitOpt.get().getCudTrans();
        if (cudTransaction >= limit) {
          throw new LimitEntityTransactionException(new LimitEntityTransactionError(entityName, limit, cudTransaction));
        }
      }
    }
  }

  /**
   * Update entity
   *
   * @param entity
   * @return
   * @throws Exception
   */
  protected ResponseEntity<T> updateEntity(final T entity) throws Exception {
    log.debug("Update Entity : {}", entity);
    DataHelper.setEmptyStringToNullOrRemoveTraillingSpaces(entity);
    T existingEntity = null;
    ResponseEntity<T> resultEntity = null;

    if (entity.getId() == null) {
      return create(entity);
    }
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    if (entity instanceof ProposeRequest) {
      // TODO Only admin and all edit rights can edit ProposeChangeEntity
      // When a proposal is refused, it runs here
      // Owner of entity and referenced entity owner can edit ProposeChangeEntity
      resultEntity = updateSaveEntity(entity, null);

      logAddUpdDel(((ProposeChangeEntity) entity).getLastModifiedBy(), entity, OperationType.UPDATE);
    } else if (entity instanceof TenantBaseID) {
      existingEntity = checkAndSetEntityWithTenant(entity, user);
      resultEntity = updateSaveEntity(entity, existingEntity);
      logAddUpdDel(user.getIdUser(), entity.getClass().getSimpleName(), OperationType.UPDATE, false);
    } else if (entity instanceof Auditable) {
      // It runs always here for auditable, also in a case of a proposal.
      if (UserAccessHelper.hasHigherPrivileges(user)) {
        // User with "admin" and "all edit" rights can change every entity
        resultEntity = checkProposeChangeAndSave(user, entity, (Auditable) entity, true);
      } else {
        resultEntity = checkProposeChangeAndSave(user, entity, (Auditable) entity, false);
      }
      logAddUpdDel(user.getIdUser(), entity, OperationType.UPDATE);
    } else {
      // Special implementation, for example rights are checked by a parent entity
      resultEntity = updateSpecialEntity(user, entity);
    }
    return resultEntity;
  }

  protected void logAddUpdDel(Integer idUser, T entity, OperationType operationType) {
    logAddUpdDel(idUser, entity.getClass().getSimpleName(), operationType, entity instanceof Auditable);
  }

  protected void logAddUpdDel(Integer idUser, String entityName, OperationType operationType, boolean isAuditable) {
    UserEntityChangeCount userEntityChangeCount = userEntityChangeCountJpaRepository
        .findById(new UserEntityChangeCountId(idUser, new Date(), entityName))
        .orElse(new UserEntityChangeCount(new UserEntityChangeCountId(idUser, new Date(), entityName)));
    userEntityChangeCount.incrementCounter(operationType);
    userEntityChangeCountJpaRepository.save(userEntityChangeCount);
  }

  protected ResponseEntity<T> updateSpecialEntity(User user, T entity) throws Exception {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  protected ResponseEntity<T> updateSaveEntity(T entity, T existingEntity) throws Exception {
    final T result = getUpdateCreateJpaRepository().saveOnlyAttributes(entity, existingEntity,
        getUpdateCreateJpaRepository().getUpdatePropertyLevels(existingEntity));
    return ResponseEntity.ok().body(result);
  }

  protected ResponseEntity<T> checkProposeChangeAndSave(final User user, T entity, Auditable parentEntity,
      boolean hasHigherPrivileges) throws Exception {
    ResponseEntity<T> result = null;
    checkDailyLimitAuditable(entity, user);

    T existingEntity = getUpdateCreateJpaRepository().findById(entity.getId()).orElse(null);
    // Since existingEntity will be updated with new Data a detach of the entity is
    // required otherwise version will not work as expected
    entityManager.detach(existingEntity);

    if (hasHigherPrivileges || hasRightsForEditingEntity(user, entity, existingEntity, parentEntity)) {
      // user can change its own created entity
      result = changeEntityWithPossibleProposals(user, entity, existingEntity);
    } else {
      return createProposaleChangeResponse(entity, existingEntity, parentEntity);
    }
    return result;
  }

  protected ResponseEntity<T> changeEntityWithPossibleProposals(final User userAtWork, T entity, T existingEntity)
      throws Exception {
    ResponseEntity<T> result = null;
    Integer idProposeRequest = ((ProposeTransientTransfer) entity).getIdProposeRequest();
    if (idProposeRequest != null) {
      // It is a proposed change entity
      Optional<ProposeChangeEntity> proposeChangeEntityOpt = proposeChangeEntityJpaRepository
          .findById(idProposeRequest);
      if (proposeChangeEntityOpt.isPresent()) {
        ProposeChangeEntity proposeChangeEntity = proposeChangeEntityOpt.get();
        if (proposeChangeEntity.getEntity().equals(entity.getClass().getSimpleName())
            && proposeChangeEntity.getIdEntity().equals(entity.getId())) {
          proposeChangeEntity.setDataChangeState(ProposeDataChangeState.ACCEPTED);
          proposeChangeEntity.setNoteAcceptReject(((ProposeTransientTransfer) entity).getNoteRequestOrReject());
          result = updateSaveEntity(entity, existingEntity);
          proposeChangeEntityJpaRepository.save(proposeChangeEntity);
        }

      }
    } else {
      // No proposale -> User has higher privileges or is owner of the entity
      result = updateSaveEntity(entity, existingEntity);
    }
    return result;
  }

  /**
   * User can't change entity if another user created it -> create a proposal
   * change
   *
   * @param entity
   * @param existingEntity
   * @param parentEntity
   * @return
   * @throws IllegalAccessException
   * @throws InvocationTargetException
   * @throws NoSuchMethodException
   */
  private ResponseEntity<T> createProposaleChangeResponse(T entity, T existingEntity, Auditable parentEntity)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    return ResponseEntity.ok().body(this.createProposaleChange(entity, existingEntity, parentEntity));
  }

  protected T createProposaleChange(T entity, T existingEntity, Auditable parentEntity)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    List<ProposeChangeField> proposeChangeFieldList = DataHelper.getDiffPropertiesOfEntity(entity, existingEntity,
        getUpdateCreateJpaRepository().getUpdatePropertyLevels(existingEntity));

    if (!proposeChangeFieldList.isEmpty()) {
      final ProposeChangeEntity proposeChangeEntityNew = proposeChangeEntityJpaRepository
          .save(new ProposeChangeEntity(entity.getClass().getSimpleName(), entity.getId(), parentEntity.getCreatedBy(),
              ((ProposeTransientTransfer) entity).getNoteRequestOrReject()));
      proposeChangeFieldList.forEach(
          proposeChangeField -> proposeChangeField.setIdProposeRequest(proposeChangeEntityNew.getIdProposeRequest()));
      proposeChangeFieldJpaRepository.saveAll(proposeChangeFieldList);
    }
    return existingEntity;
  }

  /**
   * Override this too over rule the standard rights on writing entities without
   * proposals change.
   *
   * @param user
   * @param existingEntity
   * @param parentEntity
   * @return
   */
  protected boolean hasRightsForEditingEntity(User user, T newEntity, T existingEntity, Auditable parentEntity) {
    return UserAccessHelper.hasRightsForEditingOrDeleteOnEntity(user, (Auditable) existingEntity);
  }

  /**
   * Only for entities with a tenant id, that means private data. It checks that
   * the entity has the right tenant or no tenant set. For an existing entity it
   * will be loaded, checked and returned.
   *
   * @param entity
   * @param user
   * @return
   */
  protected T checkAndSetEntityWithTenant(T entity, final User user) {
    T existingEntity = null;

    if (((TenantBaseID) entity).getIdTenant() != null) {
      if (!user.getIdTenant().equals(((TenantBaseID) entity).getIdTenant())) {
        throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
      }
    }

    // Maybe the tenant is not set by the client, we set it always
    ((TenantBaseID) entity).setIdTenant(user.getIdTenant());

    if (entity.getId() != null) {
      existingEntity = getUpdateCreateJpaRepository().findById(entity.getId()).orElse(null);
      if (existingEntity != null) {

        if (!user.getIdTenant().equals(((TenantBaseID) existingEntity).getIdTenant())) {
          throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
        }
      } else {
        // TODO Not existing ID -> should not happened
      }
    } else {
      // New Entity with Tenant
      return entity;
    }
    return existingEntity;
  }

}
