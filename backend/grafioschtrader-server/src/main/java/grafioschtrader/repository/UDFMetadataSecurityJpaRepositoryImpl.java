package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;

import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.repository.UDFMetadataSecurityJpaRepository.UiOrderDescriptionCount;

public class UDFMetadataSecurityJpaRepositoryImpl extends BaseRepositoryImpl<UDFMetadataSecurity>
    implements UDFMetadataSecurityJpaRepositoryCustom {

  @Autowired
  private UDFMetadataSecurityJpaRepository uMetaRepository;

  @Override
  public UDFMetadataSecurity saveOnlyAttributes(final UDFMetadataSecurity uDFMetadataSecurity,
      final UDFMetadataSecurity existingEntity, final Set<Class<? extends Annotation>> updatePropertyLevelClasses)
      throws Exception {
    UiOrderDescriptionCount uodc = uMetaRepository.countUiOrderAndDescription(uDFMetadataSecurity.getUiOrder(),
        uDFMetadataSecurity.getDescription());
    if (uodc.getCountDescription() > 0 && existingEntity != null
        && !existingEntity.getDescription().equals(uDFMetadataSecurity.getDescription())) {
      throw new IllegalArgumentException("Own description must be unique!");
    }

    if (uodc.getCountUiOrder() > 0 && existingEntity != null
        && existingEntity.getUiOrder() != uDFMetadataSecurity.getUiOrder()) {
      throw new IllegalArgumentException("The order GUI must be unique!");
    }

    return RepositoryHelper.saveOnlyAttributes(uMetaRepository, uDFMetadataSecurity, existingEntity,
        updatePropertyLevelClasses);
  }

}
