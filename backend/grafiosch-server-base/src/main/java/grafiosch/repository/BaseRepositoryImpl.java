package grafiosch.repository;

import java.lang.annotation.Annotation;
import java.util.Set;

import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.common.PropertySelectiveUpdatableOrWhenNull;

public abstract class BaseRepositoryImpl<T> {

  public Set<Class<? extends Annotation>> getUpdatePropertyLevels(final T existingEntity) {
    return Set.of(PropertySelectiveUpdatableOrWhenNull.class, PropertyAlwaysUpdatable.class);
  }

}
