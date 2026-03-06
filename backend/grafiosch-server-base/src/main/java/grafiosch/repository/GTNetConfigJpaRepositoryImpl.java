package grafiosch.repository;

import java.lang.annotation.Annotation;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;

import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.entities.GTNetConfig;

/**
 * Implementation of GTNetConfigJpaRepositoryCustom.
 * Handles selective attribute updates for GTNetConfig, allowing only
 * the connectionTimeout field to be modified (marked with @PropertyAlwaysUpdatable).
 */
public class GTNetConfigJpaRepositoryImpl extends BaseRepositoryImpl<GTNetConfig>
    implements GTNetConfigJpaRepositoryCustom {

  @Autowired
  private GTNetConfigJpaRepositoryBase gtNetConfigJpaRepository;

  @Override
  public GTNetConfig saveOnlyAttributes(GTNetConfig newEntity, GTNetConfig existingEntity,
      Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {
    return RepositoryHelper.saveOnlyAttributes(gtNetConfigJpaRepository, newEntity, existingEntity,
        updatePropertyLevelClasses);
  }

  @Override
  public Set<Class<? extends Annotation>> getUpdatePropertyLevels(GTNetConfig existingEntity) {
    return Set.of(PropertyAlwaysUpdatable.class);
  }
}
