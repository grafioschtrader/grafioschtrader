package grafiosch.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.lang.reflect.InvocationTargetException;
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

import grafiosch.BaseConstants;
import grafiosch.common.DataHelper;
import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.common.PropertySelectiveUpdatableOrWhenNull;
import grafiosch.common.UserAccessHelper;
import grafiosch.entities.Auditable;
import grafiosch.entities.BaseID;
import grafiosch.entities.ProposeChangeEntity;
import grafiosch.entities.ProposeChangeField;
import grafiosch.entities.ProposeRequest;
import grafiosch.entities.ProposeTransientTransfer;
import grafiosch.entities.TenantBase;
import grafiosch.entities.TenantBaseID;
import grafiosch.entities.User;
import grafiosch.entities.UserBaseID;
import grafiosch.entities.UserEntityChangeLimit;
import grafiosch.repository.ProposeChangeEntityJpaRepository;
import grafiosch.repository.ProposeChangeFieldJpaRepository;
import grafiosch.repository.TenantLimitsHelper;
import grafiosch.types.OperationType;
import grafiosch.types.ProposeDataChangeState;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.Valid;

//@formatter:off
/**
 * Abstract REST controller for creating and updating entities of type {@code T}.
 * It handles common concerns such as:
 * <ul>
 * <li>JSON request/response handling.</li>
 * <li>User authentication and authorization.</li>
 * <li>Tenant-based and user-based data access control.</li>
 * <li>Entity validation.</li>
 * <li>Proposal mechanism for changes made by users without direct edit rights.</li>
 * <li>Daily limits for CRUD operations.</li>
 * <li>Audit logging for create, update, and delete operations.</li>
 * </ul>
 * Subclasses must implement {@link #getUpdateCreateJpaRepository()} to provide
 * the specific JPA repository for the entity type {@code T}.
 *
 * @param <T> The type of the entity, which must extend {@link BaseID}.
 */
//@formatter:on
public abstract class UpdateCreate<T extends BaseID<Integer>> extends DailyLimitUpdCreateLogger<T> {

  @PersistenceContext
  private EntityManager entityManager;

  @Autowired
  private ProposeChangeFieldJpaRepository proposeChangeFieldJpaRepository;

  @Autowired
  protected ProposeChangeEntityJpaRepository proposeChangeEntityJpaRepository;

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  protected abstract UpdateCreateJpaRepository<T> getUpdateCreateJpaRepository();

  /**
   * Request for a new entity. The new URI-location of the created record is not returned.
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
   * @param entity The entity to be created.
   * @return ResponseEntity containing the created entity or an error status.
   * @throws Exception If any error occurs during entity creation.
   */
  protected ResponseEntity<T> createEntity(T entity) throws Exception {
    log.debug("Create Entity : {}", entity);
    DataHelper.setEmptyStringToNullOrRemoveTraillingSpaces(entity);
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    if (entity.getId() != null) {
      return ResponseEntity.badRequest().header("Failure", "Cannot create Entity with existing ID").body(null);
    }

    if (entity instanceof TenantBaseID) {
      if (entity instanceof TenantBase) {
        // User can have only one Tenant
        if (user.getIdTenant() != null) {
          throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
        }
      } else {
        entity = checkAndSetEntityWithTenant(entity, user);
      }
      if (!TenantLimitsHelper.canAddWhenCheckedAgainstMayBeExistingTenantLimit(entityManager, entity)) {
        throw new SecurityException(BaseConstants.LIMIT_SECURITY_BREACH);
      }
    } else {
      if (entity instanceof UserBaseID) {
        checkAndSetEntityWithUser(entity, user);
      }
      checkDailyLimitOnCRUDOperations(entity, user);
    }
    final T result = getUpdateCreateJpaRepository().saveOnlyAttributes(entity, null,
        Set.of(PropertySelectiveUpdatableOrWhenNull.class, PropertyAlwaysUpdatable.class));

    logAddUpdDel(user.getIdUser(), result, OperationType.ADD);
    if (entity instanceof UserEntityChangeLimit && ((UserEntityChangeLimit) entity).getIdProposeRequest() != null) {
      // UserEntityChangeLimit can have a proposal request without an existing
      // UserEntityChangeLimit, because the
      // user which caused it, produces only a proposal on non existing entity
      updateEntity(entity);
    }
    return ResponseEntity.ok().body(result);
  }

  /**
   * Update entity
   *
   * @param entity The entity to be updated.
   * @return ResponseEntity containing the updated entity or an error status.
   * @throws Exception If any error occurs during entity update.
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
      logAddUpdDel(user.getIdUser(), entity.getClass().getSimpleName(), OperationType.UPDATE);
    } else if (entity instanceof Auditable) {
      // It runs always here for auditable, also in a case of a proposal.
      if (UserAccessHelper.hasHigherPrivileges(user)) {
        // User with "admin" and "all edit" rights can change every entity
        resultEntity = checkProposeChangeAndSave(user, entity, (Auditable) entity, true);
      } else {
        resultEntity = checkProposeChangeAndSave(user, entity, (Auditable) entity, false);
      }
      logAddUpdDel(user.getIdUser(), entity, OperationType.UPDATE);
    } else if (entity instanceof UserBaseID) {
      existingEntity = checkAndSetEntityWithUser(entity, user);
      resultEntity = updateSaveEntity(entity, existingEntity);
      logAddUpdDel(user.getIdUser(), entity.getClass().getSimpleName(), OperationType.UPDATE);
    } else {
      // Special implementation, for example rights are checked by a parent entity
      resultEntity = updateSpecialEntity(user, entity);
    }
    return resultEntity;
  }

  /**
   * Placeholder for special entity update logic. Subclasses can override this to implement custom update behavior
   * for specific entity types not covered by the general logic.
   *
   * @param user   The user performing the update.
   * @param entity The entity to be updated.
   * @return ResponseEntity containing the updated entity or an error status.
   * @throws Exception                     If any error occurs during the special update.
   * @throws UnsupportedOperationException By default, if not overridden.
   * 
   */
  protected ResponseEntity<T> updateSpecialEntity(User user, T entity) throws Exception {
    T existingEntity = getUpdateCreateJpaRepository().findById(entity.getId()).orElse(null);
    if (existingEntity == null) {
      return ResponseEntity.notFound().build();
    }
    return updateSaveEntity(entity, existingEntity);
  }
  

  /**
   * Saves the entity (either new or existing) to the repository. Only attributes marked with specified update property
   * levels are saved.
   *
   * @param entity         The entity with new values.
   * @param existingEntity The existing entity from the database (can be null for new entities).
   * @return ResponseEntity containing the saved entity.
   * @throws Exception If any error occurs during saving.
   */
  protected ResponseEntity<T> updateSaveEntity(T entity, T existingEntity) throws Exception {
    final T result = getUpdateCreateJpaRepository().saveOnlyAttributes(entity, existingEntity,
        getUpdateCreateJpaRepository().getUpdatePropertyLevels(existingEntity));
    return ResponseEntity.ok().body(result);
  }

  /**
   * Checks if a proposal change is required or if the user has direct editing rights, then proceeds to save or propose
   * the changes.
   *
   * @param user                The user performing the operation.
   * @param entity              The entity with new values.
   * @param parentEntity        The auditable parent entity.
   * @param hasHigherPrivileges Flag indicating if the user has administrative or higher privileges.
   * @return ResponseEntity containing the updated entity or the existing entity if a proposal was created.
   * @throws Exception If any error occurs.
   */
  protected ResponseEntity<T> checkProposeChangeAndSave(final User user, T entity, Auditable parentEntity,
      boolean hasHigherPrivileges) throws Exception {
    ResponseEntity<T> result = null;
    checkDailyLimitOnCRUDOperations(entity, user);

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

  /**
   * Handles changes to an entity, including accepting existing proposals if applicable.
   *
   * @param userAtWork     The user performing the change.
   * @param entity         The entity with new values.
   * @param existingEntity The existing entity from the database.
   * @return ResponseEntity containing the updated entity.
   * @throws Exception If any error occurs.
   */
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
      // No proposal -> User has higher privileges or is owner of the entity
      result = updateSaveEntity(entity, existingEntity);
    }
    return result;
  }

  /**
   * User can't change entity if another user created it -> create a proposal change
   *
   * @param entity         The entity with the proposed changes.
   * @param existingEntity The existing entity from the database.
   * @param parentEntity   The parent auditable entity.
   * @return ResponseEntity containing the existing entity (as proposal is created).
   */
  private ResponseEntity<T> createProposaleChangeResponse(T entity, T existingEntity, Auditable parentEntity)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    return ResponseEntity.ok().body(this.createProposaleChange(entity, existingEntity, parentEntity));
  }

  /**
   * Creates and saves a {@link ProposeChangeEntity} and its associated {@link ProposeChangeField}s based on the
   * differences between the proposed entity and the existing entity.
   *
   * @param entity         The entity with the proposed changes.
   * @param existingEntity The current state of the entity in the database.
   * @param parentEntity   The parent auditable entity, used to determine the owner of the original entity.
   * @return The existing entity, as no direct changes are made to it in this step.
   */
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
   * Override this too over rule the standard rights on writing entities without proposals change.
   *
   * @param user           The user attempting the edit.
   * @param newEntity      The entity with new values.
   * @param existingEntity The existing entity from the database.
   * @param parentEntity   The parent auditable entity.
   * @return {@code true} if the user has rights to edit, {@code false} otherwise.
   */
  protected boolean hasRightsForEditingEntity(User user, T newEntity, T existingEntity, Auditable parentEntity) {
    return UserAccessHelper.hasRightsForEditingOrDeleteOnEntity(user, (Auditable) existingEntity);
  }

  /**
   * Only for entities with a tenant id, that means private data. It checks that the entity has the right tenant or no
   * tenant set. For an existing entity it will be loaded, checked and returned.
   *
   * @param entity The entity to check and set the tenant for.
   * @param user   The currently authenticated user.
   * @return The existing entity if found and tenant matches, or the input entity if new.
   * @throws SecurityException If a security breach is detected (e.g., tenant mismatch).
   */
  protected T checkAndSetEntityWithTenant(T entity, final User user) {
    T existingEntity = null;

    if (((TenantBaseID) entity).getIdTenant() != null) {
      if (!user.getIdTenant().equals(((TenantBaseID) entity).getIdTenant())) {
        throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
      }
    }

    // Maybe the tenant is not set by the client, we set it always
    ((TenantBaseID) entity).setIdTenant(user.getIdTenant());

    if (entity.getId() != null) {
      existingEntity = getUpdateCreateJpaRepository().findById(entity.getId()).orElse(null);
      if (existingEntity != null) {
        if (!user.getIdTenant().equals(((TenantBaseID) existingEntity).getIdTenant())) {
          throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
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

  /**
   * Certain entities have a relation with the user and not with the tenant. Therefore, this must be checked to ensure
   * that it is not misused. The user ID in the security context must match the user ID of the entity.
   *
   * @param entity The entity to check and set the user for.
   * @param user   The currently authenticated user.
   * @return The existing entity if found and user ID matches, or the input entity if new.
   * @throws SecurityException If a security breach is detected (e.g., user ID mismatch).
   */
  protected T checkAndSetEntityWithUser(T entity, final User user) {
    T existingEntity = null;

    checkAndSetUserBaseIDWithUser((UserBaseID) entity, user);

    if (entity.getId() != null) {
      existingEntity = getUpdateCreateJpaRepository().findById(entity.getId()).orElse(null);
      if (existingEntity != null) {
        if (!user.getIdUser().equals(((UserBaseID) existingEntity).getIdUser())) {
          throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
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

  /**
   * Utility method to check and set the user ID on a {@link UserBaseID} entity. If the entity already has a user ID, it
   * verifies it against the provided user's ID. Otherwise, it sets the entity's user ID to the provided user's ID.
   *
   * @param entity The {@link UserBaseID} entity to check/modify.
   * @param user   The user whose ID should be set or checked against.
   * @throws SecurityException If the entity's user ID is already set and does not match the provided user's ID.
   */
  public static void checkAndSetUserBaseIDWithUser(UserBaseID entity, final User user) {
    if (entity.getIdUser() != null) {
      if (!user.getIdUser().equals(entity.getIdUser())) {
        throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
      }
    }
    // Maybe the user is not set by the client, we set it always
    entity.setIdUser(user.getIdUser());
  }

}
