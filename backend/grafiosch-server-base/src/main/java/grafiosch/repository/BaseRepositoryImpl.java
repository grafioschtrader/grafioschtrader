package grafiosch.repository;

import java.lang.annotation.Annotation;
import java.util.Set;

import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.common.PropertySelectiveUpdatableOrWhenNull;

/**
 * Abstract base implementation for custom repository operations. Provides default property-level update permission
 * logic that can be overridden by subclasses.
 * 
 * @param <T> the entity type handled by this repository
 */
public abstract class BaseRepositoryImpl<T> {

  /**
   * Gets the default property-level update permissions for entity attribute modifications. Returns a standard set of
   * annotation classes that allow selective and always-updatable properties. Subclasses can override this method to
   * provide entity-specific or context-aware permission logic.
   * 
   * @param existingEntity the existing entity to determine update permissions for (not used in default implementation)
   * @return set containing PropertySelectiveUpdatableOrWhenNull and PropertyAlwaysUpdatable annotation classes
   */
  public Set<Class<? extends Annotation>> getUpdatePropertyLevels(final T existingEntity) {
    return Set.of(PropertySelectiveUpdatableOrWhenNull.class, PropertyAlwaysUpdatable.class);
  }

}
