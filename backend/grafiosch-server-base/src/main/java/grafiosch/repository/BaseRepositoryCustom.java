package grafiosch.repository;

import java.lang.annotation.Annotation;
import java.util.Set;

/**
 * Base custom repository interface providing selective attribute update functionality. Enables fine-grained control
 * over entity property updates based on annotation-driven permission levels.
 * 
 * @param <T> the entity type handled by this repository
 */
public interface BaseRepositoryCustom<T> {
  /**
   * Saves only specific attributes of an entity based on property-level update permissions. Uses annotation-based
   * access control to determine which fields can be modified by the current user.
   * 
   * @param newEntity                  the entity containing the new values to be saved
   * @param existingEntity             the current entity from the database, null for new entities
   * @param updatePropertyLevelClasses set of annotation classes that define update permission levels
   * @return the saved entity with updated attributes
   * @throws Exception if the save operation fails, validation errors occur, or security constraints are violated
   */
  T saveOnlyAttributes(T newEntity, final T existingEntity, Set<Class<? extends Annotation>> updatePropertyLevelClasses)
      throws Exception;

  /**
   * Determines the property-level update permissions for the current user context. Analyzes user roles and entity state
   * to return applicable annotation classes for field-level access control.
   * 
   * @param existingEntity the existing entity to determine update permissions for
   * @return set of annotation classes representing the user's update permission levels
   */
  Set<Class<? extends Annotation>> getUpdatePropertyLevels(final T existingEntity);
}
