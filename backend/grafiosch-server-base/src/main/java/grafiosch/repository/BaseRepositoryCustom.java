package grafiosch.repository;

import java.lang.annotation.Annotation;
import java.util.Set;

public interface BaseRepositoryCustom<T> {
  T saveOnlyAttributes(T newEntity, final T existingEntity,
      Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception;

  Set<Class<? extends Annotation>> getUpdatePropertyLevels(final T existingEntity);

}
