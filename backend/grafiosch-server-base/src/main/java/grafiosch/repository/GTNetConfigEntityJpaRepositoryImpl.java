package grafiosch.repository;

import java.lang.annotation.Annotation;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;

import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.entities.GTNetConfigEntity;

/**
 * Implementation of GTNetConfigEntityJpaRepositoryCustom.
 * Handles selective attribute updates for GTNetConfigEntity, allowing only
 * useDetailLog and consumerUsage fields to be modified (marked with @PropertyAlwaysUpdatable).
 */
public class GTNetConfigEntityJpaRepositoryImpl extends BaseRepositoryImpl<GTNetConfigEntity>
    implements GTNetConfigEntityJpaRepositoryCustom {

  @Autowired
  private GTNetConfigEntityJpaRepository gtNetConfigEntityJpaRepository;

  @Override
  public GTNetConfigEntity saveOnlyAttributes(GTNetConfigEntity newEntity, GTNetConfigEntity existingEntity,
      Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {
    return RepositoryHelper.saveOnlyAttributes(gtNetConfigEntityJpaRepository, newEntity, existingEntity,
        updatePropertyLevelClasses);
  }

  @Override
  public Set<Class<? extends Annotation>> getUpdatePropertyLevels(GTNetConfigEntity existingEntity) {
    // Only allow updating fields marked with @PropertyAlwaysUpdatable
    // This restricts updates to useDetailLog and consumerUsage only
    return Set.of(PropertyAlwaysUpdatable.class);
  }
}
