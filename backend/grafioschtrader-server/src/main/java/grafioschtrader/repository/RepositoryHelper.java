package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.common.DataHelper;
import grafioschtrader.entities.BaseID;

public class RepositoryHelper {

  @Transactional
  @Modifying
  public static <T extends BaseID> T saveOnlyAttributes(final JpaRepository<T, Integer> jpaRepository, final T entity,
      final T existingEntity, final Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {
    if (existingEntity != null) {
      DataHelper.updateEntityWithUpdatable(entity, existingEntity, updatePropertyLevelClasses);
      return jpaRepository.save(existingEntity);
    } else {
      return jpaRepository.save(entity);
    }
  }

}
