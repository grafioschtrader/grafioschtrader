package grafiosch.repository;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.SerializationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.common.UserAccessHelper;
import grafiosch.entities.ProposeChangeEntity;
import grafiosch.entities.User;
import grafiosch.types.ProposeDataChangeState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.metamodel.EntityType;

/**
 * Implementation of custom repository operations for managing entity change proposals. This repository handles the
 * workflow of processing change requests for shared entities, including privilege-based access control, entity
 * state comparison, and proposal lifecycle management.</br>
 * 
 * The implementation provides functionality for:</br>
 * - Retrieving open proposals based on user access privileges</br>
 * - Creating comparison views between current and proposed entity states</br>
 * - Cleaning up orphaned proposals where target entities no longer exist</br>
 * - Dynamic entity class resolution from proposal metadata</br>
 * 
 * Access control ensures that users with higher privileges can review all proposals, while regular users can only
 * access proposals for entities they own.
 */
public class ProposeChangeEntityJpaRepositoryImpl extends ProposeRequestService<ProposeChangeEntity>
    implements ProposeChangeEntityJpaRepositoryCustom {

  @PersistenceContext
  private EntityManager entityManager;

  @Autowired
  private EntityManagerFactory entityManagerFactory;

  @Autowired
  private ProposeChangeEntityJpaRepository proposeChangeEntityJpaRepository;

  @Override
  @Transactional
  @Modifying
  public ProposeChangeEntity saveOnlyAttributes(final ProposeChangeEntity proposeChangeEntity,
      final ProposeChangeEntity existingEntity, final Set<Class<? extends Annotation>> updatePropertyLevelClasses) {
    return proposeChangeEntityJpaRepository.save(proposeChangeEntity);
  }

  @Override
  public List<ProposeChangeEntityWithEntity> getProposeChangeEntityWithEntity() throws Exception {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    List<ProposeChangeEntityWithEntity> proposeChangeEntityWithEntityList = new ArrayList<>();
    List<ProposeChangeEntity> proposeChangeEntityList;

    if (UserAccessHelper.hasHigherPrivileges(user)) {
      proposeChangeEntityList = proposeChangeEntityJpaRepository
          .findByDataChangeState(ProposeDataChangeState.OPEN.getValue());
    } else {
      proposeChangeEntityList = proposeChangeEntityJpaRepository.findByIdOwnerEntityAndDataChangeState(user.getIdUser(),
          ProposeDataChangeState.OPEN.getValue());
    }

    for (ProposeChangeEntity proposeChangeEntity : proposeChangeEntityList) {
      Class<?> entityClass = getEntityClass(proposeChangeEntity.getEntity());
      Object entityExisting = entityManager.find(entityClass, proposeChangeEntity.getIdEntity());
      if (entityExisting != null) {
        entityManager.detach(entityExisting);
        Object entityProposed = SerializationUtils.clone((Serializable) entityExisting);
        copyProposeChangeFieldToBusinessClass(proposeChangeEntity.getProposeChangeFieldList(), entityProposed);
        proposeChangeEntityWithEntityList
            .add(new ProposeChangeEntityWithEntity(proposeChangeEntity, entityExisting, entityProposed));
      } else {
        proposeChangeEntityJpaRepository.deleteById(proposeChangeEntity.getIdProposeRequest());
      }
    }
    return proposeChangeEntityWithEntityList;
  }

  /**
   * Resolves an entity class from its simple name using JPA metamodel introspection. This method dynamically looks up
   * entity classes registered in the JPA metamodel to convert string-based entity names stored in proposals back to
   * their corresponding Java class objects for reflection and instantiation operations.
   * 
   * The lookup process uses functional programming to filter through all registered entity types in the JPA metamodel
   * and match the simple class name against the provided entity name. This approach allows the system to work with
   * entity references stored as strings while maintaining type safety during entity manipulation.
   *
   * @param entityName the simple name of the entity class to resolve
   * @return the Java Class object corresponding to the entity name
   * @throws IllegalArgumentException if no entity class matches the provided name
   */
  private Class<?> getEntityClass(String entityName) {
    return entityManagerFactory.getMetamodel().getEntities().stream().map(EntityType::getJavaType)
        .filter(clazz -> clazz.getSimpleName().equals(entityName)).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("No entity found with name: " + entityName));
  }

  @Schema(description = "Contains a change request for a shared entity")
  public static class ProposeChangeEntityWithEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "Contains the proposal for changing the entity.")
    public ProposeChangeEntity proposeChangeEntity;
    @Schema(description = "The entity as it currently is")
    public Object entity;
    @Schema(description = "The entity as proposed, so this contains the changes")
    public Object proposedEntity;

    public ProposeChangeEntityWithEntity(ProposeChangeEntity proposeChangeEntity, Object entity,
        Object proposedEntity) {
      super();
      this.proposeChangeEntity = proposeChangeEntity;
      this.entity = entity;
      this.proposedEntity = proposedEntity;
    }
  }

}
