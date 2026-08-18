package grafiosch.repository;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.dto.InputRule;
import grafiosch.dto.LimitKey;
import grafiosch.dto.LimitKeyDefinition;
import grafiosch.entities.AdminEntity;
import grafiosch.entities.BaseID;
import grafiosch.entities.EntityLimit;
import grafiosch.entities.TenantBaseID;
import grafiosch.exceptions.DataViolationException;
import grafiosch.limit.EntityLimitCache;
import grafiosch.limit.LimitKeyRegistry;
import grafiosch.types.LimitType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;

/**
 * Custom repository operations for {@link EntityLimit}: validating a row against the limit key catalogue before it is
 * written, and producing the key definitions the administration UI offers.
 */
public class EntityLimitJpaRepositoryImpl extends BaseRepositoryImpl<EntityLimit>
    implements EntityLimitJpaRepositoryCustom {

  @Autowired
  private EntityLimitJpaRepository entityLimitJpaRepository;

  @Autowired
  private EntityLimitCache entityLimitCache;

  @Autowired
  private EntityManager entityManager;

  @Override
  public EntityLimit saveOnlyAttributes(EntityLimit entityLimit, EntityLimit existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {
    LimitKey limitKey = resolveAndValidateKey(entityLimit);
    validateScope(entityLimit);
    validateLimitValue(entityLimit, limitKey);

    // A user holds at most one row per key. When a limit-increase request is approved the frontend sends a row without
    // id, which would insert and collide with an already existing — often expired — row for the same user and key.
    // Resolve that row here and pass it as existingEntity so the save renews it instead of violating the unique key.
    if (existingEntity == null && entityLimit.getIdEntityLimit() == null && entityLimit.getIdUser() != null
        && limitKey.limitType() != LimitType.MAX) {
      existingEntity = entityLimitJpaRepository.findByIdUserAndLimitTypeAndEntityName(entityLimit.getIdUser(),
          limitKey.limitType().getValue(), limitKey.entityName()).orElse(null);
    }
    validateNoDuplicate(entityLimit, limitKey, existingEntity);
    EntityLimit saved = RepositoryHelper.saveOnlyAttributes(entityLimitJpaRepository, entityLimit, existingEntity,
        updatePropertyLevelClasses);
    entityLimitCache.evictAll();
    return saved;
  }

  /**
   * Expands the posted key id into the five key columns and rejects anything that is neither a registered {@code MAX}
   * key nor a derived daily key. On an update the columns are already set and the key id may be absent.
   */
  private LimitKey resolveAndValidateKey(EntityLimit entityLimit) {
    LimitKey limitKey;
    if (entityLimit.getKeyId() != null && !entityLimit.getKeyId().isBlank()) {
      try {
        limitKey = LimitKey.parse(entityLimit.getKeyId());
      } catch (IllegalArgumentException e) {
        throw new DataViolationException("key.id", "g.entity.limit.key.unknown",
            new Object[] { entityLimit.getKeyId() });
      }
      entityLimit.setLimitKey(limitKey);
    } else {
      limitKey = entityLimit.getLimitKey();
    }
    if (!isKnownKey(limitKey)) {
      throw new DataViolationException("key.id", "g.entity.limit.key.unknown", new Object[] { limitKey.keyId() });
    }
    return limitKey;
  }

  /**
   * A MAX key must be registered. A daily key only has to name an entity of the derived set, and the read family only
   * accepts a pseudo name registered for it — a {@code HistoryquoteRead} row written as {@code DAY_CUD} would be
   * invisible to the read resolver and would silently drop that user's read exception.
   */
  private boolean isKnownKey(LimitKey limitKey) {
    return switch (limitKey.limitType()) {
    case MAX -> LimitKeyRegistry.find(limitKey).isPresent();
    case DAY_READ -> LimitKeyRegistry.isReadPseudoEntityName(limitKey.entityName());
    case DAY_CUD -> !LimitKeyRegistry.isReadPseudoEntityName(limitKey.entityName())
        && derivedDailyEntityNames().contains(limitKey.entityName());
    };
  }

  /**
   * Rejects a second row for the same key and the same scope. The unique index over the key columns and the folded
   * role and user columns would catch it as well, but only as a constraint violation the user cannot read. The
   * remaining key parts and the scope are compared in Java rather than queried, exactly as the resolver does, because
   * they are nullable and only a handful of rows share a limit type and entity name.
   */
  private void validateNoDuplicate(EntityLimit entityLimit, LimitKey limitKey, EntityLimit existingEntity) {
    Integer idEntityLimit = existingEntity != null ? existingEntity.getIdEntityLimit() : entityLimit.getIdEntityLimit();
    boolean duplicate = entityLimitJpaRepository
        .findByLimitTypeAndEntityName(limitKey.limitType().getValue(), limitKey.entityName()).stream()
        .filter(el -> idEntityLimit == null || !el.getIdEntityLimit().equals(idEntityLimit))
        .anyMatch(el -> el.getLimitKey().equals(limitKey) && Objects.equals(el.getIdRole(), entityLimit.getIdRole())
            && Objects.equals(el.getIdUser(), entityLimit.getIdUser()));
    if (duplicate) {
      throw new DataViolationException("key.id", "g.entity.limit.duplicate", new Object[] { limitKey.keyId() });
    }
  }

  private void validateScope(EntityLimit entityLimit) {
    if (entityLimit.getIdRole() != null && entityLimit.getIdUser() != null) {
      throw new DataViolationException("id.role", "g.entity.limit.role.user.exclusive", null);
    }
  }

  /**
   * Validates the value against the rule of its key. The rule belongs to the key rather than to the row, so a per-role
   * or per-user override is validated exactly like the {@code ALL} default.
   */
  private void validateLimitValue(EntityLimit entityLimit, LimitKey limitKey) {
    String inputRule = LimitKeyRegistry.find(limitKey).map(r -> r.inputRule()).orElse(null);
    InputRule rule = InputRule.parse(inputRule);
    if (rule != null) {
      String errorMsgKey = rule.validate(entityLimit.getLimitValue());
      if (errorMsgKey != null) {
        throw new DataViolationException("limit.value", errorMsgKey, new Object[] { rule.getDescription() });
      }
    }
  }

  @Override
  @Transactional
  public List<LimitKeyDefinition> getLimitKeyDefinitions(Integer idUser, Integer idEntityLimit) {
    Set<String> usedKeyIds = idUser == null ? Set.of()
        : entityLimitJpaRepository.findByIdUser(idUser)
            .filter(el -> idEntityLimit == null || !el.getIdEntityLimit().equals(idEntityLimit))
            .map(el -> el.getLimitKey().keyId()).collect(Collectors.toCollection(HashSet::new));

    List<LimitKeyDefinition> definitions = new ArrayList<>();
    LimitKeyRegistry.getMaxRegistrations().stream().map(r -> r.toDefinition())
        .filter(d -> !usedKeyIds.contains(d.keyId)).forEach(definitions::add);

    derivedDailyEntityClasses().forEach(
        (entityName, clazz) -> addDerivedDefinition(definitions, usedKeyIds, LimitKey.dayCud(entityName), clazz));
    LimitKeyRegistry.getReadPseudoEntityClasses().forEach(
        (entityName, clazz) -> addDerivedDefinition(definitions, usedKeyIds, LimitKey.dayRead(entityName), clazz));
    return definitions;
  }

  private void addDerivedDefinition(List<LimitKeyDefinition> definitions, Set<String> usedKeyIds, LimitKey limitKey,
      Class<?> entityClass) {
    if (!usedKeyIds.contains(limitKey.keyId())) {
      definitions.add(new LimitKeyDefinition(limitKey, LimitKeyRegistry.toLabelKey(limitKey.entityName()), null, null,
          null, false, LimitKeyRegistry.isSharedData(entityClass)));
    }
  }

  /**
   * The entity names a daily CUD limit may target, see {@link #derivedDailyEntityClasses()}.
   */
  private Set<String> derivedDailyEntityNames() {
    return derivedDailyEntityClasses().keySet();
  }

  /**
   * The entities a daily CUD limit may target, by name: every concrete, non-abstract {@link BaseID} entity that is
   * neither tenant-owned nor an {@link AdminEntity}, taken from the JPA metamodel, plus the registered pseudo names.
   * This mirrors {@code UpdateCreate.createEntity}: tenant-owned entities bypass the daily check, while auditable,
   * user-owned and plain shared entities all pass through it. The set deliberately remains wider than the keys that
   * have a seeded value — an entity nobody configured is simply unlimited.
   *
   * <p>
   * The class is carried along only so that the administration UI can be told whether the entity holds private or
   * shared data; the counter of a daily key is always {@code user_entity_change_count}.
   * </p>
   */
  private Map<String, Class<?>> derivedDailyEntityClasses() {
    Map<String, Class<?>> entityClasses = new LinkedHashMap<>();
    for (EntityType<?> entity : entityManager.getMetamodel().getEntities()) {
      Class<?> clazz = entity.getBindableJavaType();
      if (BaseID.class.isAssignableFrom(clazz) && !Modifier.isAbstract(clazz.getModifiers())
          && !TenantBaseID.class.isAssignableFrom(clazz) && !AdminEntity.class.isAssignableFrom(clazz)) {
        entityClasses.put(entity.getName(), clazz);
      }
    }
    LimitKeyRegistry.getCudPseudoEntityClasses().forEach(entityClasses::putIfAbsent);
    return entityClasses;
  }
}
