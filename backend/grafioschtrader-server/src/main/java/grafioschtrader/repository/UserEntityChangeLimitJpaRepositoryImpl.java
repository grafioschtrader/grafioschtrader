package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.dto.ValueKeyHtmlSelectOptions;
import grafioschtrader.entities.AdminEntity;
import grafioschtrader.entities.Auditable;
import grafioschtrader.entities.UserEntityChangeLimit;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;

public class UserEntityChangeLimitJpaRepositoryImpl extends BaseRepositoryImpl<UserEntityChangeLimit>
    implements UserEntityChangeLimitJpaRepositoryCustom {

  @Autowired
  private UserEntityChangeLimitJpaRepository userEntityChangeLimitJpaRepository;

  @Autowired
  private EntityManager entityManager;

  @Override
  public UserEntityChangeLimit saveOnlyAttributes(UserEntityChangeLimit userEntityChangeLimit,
      UserEntityChangeLimit existingEntity, final Set<Class<? extends Annotation>> updatePropertyLevelClasses)
      throws Exception {
    return RepositoryHelper.saveOnlyAttributes(userEntityChangeLimitJpaRepository, userEntityChangeLimit,
        existingEntity, updatePropertyLevelClasses);
  }

  @Override
  @Transactional
  public List<ValueKeyHtmlSelectOptions> getPublicEntitiesAsHtmlSelectOptions(Integer idUser,
      Integer idUserEntityChangeLimit) {
    final List<ValueKeyHtmlSelectOptions> entitiesVKHSO = new ArrayList<>();

    Set<String> usedEntityLimits = userEntityChangeLimitJpaRepository.findByIdUser(idUser)
        .filter(userEntityChangeLimit -> idUserEntityChangeLimit == null
            || !userEntityChangeLimit.getIdUserEntityChangeLimit().equals(idUserEntityChangeLimit))
        .map(UserEntityChangeLimit::getEntityName).collect(Collectors.toCollection(HashSet::new));

    final Set<EntityType<?>> entityTypeList = entityManager.getMetamodel().getEntities();
    for (EntityType<?> entity : entityTypeList) {

      Class<?> clazz = entity.getBindableJavaType();
      if (Auditable.class.isAssignableFrom(clazz) && !Modifier.isAbstract(clazz.getModifiers())
          && !AdminEntity.class.isAssignableFrom(clazz)) {
        if (!usedEntityLimits.contains(entity.getName())) {
          entitiesVKHSO.add(new ValueKeyHtmlSelectOptions(entity.getName(), entity.getName().toUpperCase()));
        }
      }
    }
    return entitiesVKHSO;
  }
}
