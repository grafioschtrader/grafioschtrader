package grafiosch.repository;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.entities.AdminEntity;
import grafiosch.entities.Auditable;
import grafiosch.entities.UserEntityChangeLimit;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;

/**
 * Implementation of custom repository operations for user entity change limits.
 * Provides methods for selective attribute saving and for generating HTML select options for public entities.
 */
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
        addEntityNameOption(entitiesVKHSO, usedEntityLimits, entity.getName());
      }
    }
    for (String pseudoEntityName : UserEntityChangeLimit.ADDITIONAL_LIMIT_ENTITY_NAMES) {
      addEntityNameOption(entitiesVKHSO, usedEntityLimits, pseudoEntityName);
    }
    return entitiesVKHSO;
  }

  /**
   * Adds an entity name as select option with an UPPER_SNAKE_CASE display key, skipping names the user already has a
   * limit for.
   *
   * @param entitiesVKHSO    target option list
   * @param usedEntityLimits entity names already covered by an existing limit of the user
   * @param entityName       JPA entity name or registered pseudo entity name
   */
  private void addEntityNameOption(List<ValueKeyHtmlSelectOptions> entitiesVKHSO, Set<String> usedEntityLimits,
      String entityName) {
    if (!usedEntityLimits.contains(entityName)) {
      entitiesVKHSO.add(new ValueKeyHtmlSelectOptions(entityName, entityName
          .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2").replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase()));
    }
  }
}
