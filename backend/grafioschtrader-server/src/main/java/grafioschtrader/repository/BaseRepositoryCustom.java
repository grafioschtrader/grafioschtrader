package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.Set;

public interface BaseRepositoryCustom<T> {
  T saveOnlyAttributes(T assetclass, final T existingEntity,
      Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception;

  Set<Class<? extends Annotation>> getUpdatePropertyLevels(final T existingEntity);

}
