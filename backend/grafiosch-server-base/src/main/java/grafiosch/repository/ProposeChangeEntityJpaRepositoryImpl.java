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
import jakarta.persistence.PersistenceContext;

public class ProposeChangeEntityJpaRepositoryImpl extends ProposeRequestService<ProposeChangeEntity>
    implements ProposeChangeEntityJpaRepositoryCustom {

  @PersistenceContext
  private EntityManager entityManager;

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
      Class<?> c = Class.forName("grafioschtrader.entities." + proposeChangeEntity.getEntity());
      Object entityExisting = entityManager.find(c, proposeChangeEntity.getIdEntity());
      if (entityExisting != null) {
        entityManager.detach(entityExisting);
        Object entityProposed = SerializationUtils.clone((Serializable) entityExisting);
        copyProposeChangeFieldToBusinessClass(proposeChangeEntity.getProposeChangeFieldList(), entityProposed);
        proposeChangeEntityWithEntityList
            .add(new ProposeChangeEntityWithEntity(proposeChangeEntity, entityExisting, entityProposed));
      } else {
        // Entity may be removed -> delete proposal
        proposeChangeEntityJpaRepository.deleteById(proposeChangeEntity.getIdProposeRequest());
      }
    }
    return proposeChangeEntityWithEntityList;
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
