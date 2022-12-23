package grafioschtrader.repository;

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

import grafioschtrader.common.UserAccessHelper;
import grafioschtrader.entities.ProposeChangeEntity;
import grafioschtrader.entities.User;
import grafioschtrader.types.ProposeDataChangeState;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

public class ProposeChangeEntityJpaRepositoryImpl extends ProposeRequestService<ProposeChangeEntity>
    implements ProposeChangeEntityJpaRepositoryCustom {

  @PersistenceContext
  private EntityManager entityManager;

  @Autowired
  ProposeChangeEntityJpaRepository proposeChangeEntityJpaRepository;

  @Autowired
  ProposeChangeFieldJpaRepository proposeChangeFieldJpaRepository;

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

  public static class ProposeChangeEntityWithEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    public ProposeChangeEntity proposeChangeEntity;
    public Object entity;
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
