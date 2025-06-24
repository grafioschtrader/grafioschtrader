package grafiosch.repository;

import java.lang.annotation.Annotation;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.common.DataHelper;
import grafiosch.entities.BaseID;

/**
 * Utility class providing helper methods for JPA repository operations.
 * 
 * <p>
 * This class contains static methods that provide common functionality for repository operations, particularly focused
 * on selective entity updates based on annotation-driven property filtering. It helps reduce boilerplate code in
 * repository implementations and ensures consistent update behavior across the application.
 */
public class RepositoryHelper {

  /**
   * Saves an entity with selective attribute updates based on annotation filtering.
   * 
   * <p>
   * This method provides a flexible approach to entity updates by allowing selective copying of attributes from the
   * input entity to an existing entity based on annotation classes. This is particularly useful for scenarios where
   * only certain fields should be updated (e.g., during imports, partial updates, or when specific update rules apply).
   * 
   * <p>
   * The method handles both create and update scenarios:
   * <ul>
   * <li>If existingEntity is null, saves the new entity directly</li>
   * <li>If existingEntity exists, copies allowed attributes from entity to existingEntity and saves the updated
   * version</li>
   * </ul>
   * 
   * <p>
   * Attribute copying is controlled by the updatePropertyLevelClasses parameter, which specifies which annotation
   * classes mark fields as updatable. Only fields annotated with these annotation types will be copied from the source
   * entity to the existing entity.
   * 
   * @param <T>                        the entity type extending BaseID with Integer primary key
   * @param jpaRepository              the JPA repository for the entity type
   * @param entity                     the source entity containing new values to be saved or merged
   * @param existingEntity             the existing entity to update, or null for new entity creation
   * @param updatePropertyLevelClasses set of annotation classes that mark fields as updatable
   * @return the saved entity (either the new entity or the updated existing entity)
   * @throws Exception if the update process fails, including reflection errors during attribute copying
   */
  @Transactional
  @Modifying
  public static <T extends BaseID<Integer>> T saveOnlyAttributes(final JpaRepository<T, Integer> jpaRepository,
      final T entity, final T existingEntity, final Set<Class<? extends Annotation>> updatePropertyLevelClasses)
      throws Exception {
    if (existingEntity != null) {
      DataHelper.updateEntityWithUpdatable(entity, existingEntity, updatePropertyLevelClasses);
      return jpaRepository.save(existingEntity);
    } else {
      return jpaRepository.save(entity);
    }
  }

}
