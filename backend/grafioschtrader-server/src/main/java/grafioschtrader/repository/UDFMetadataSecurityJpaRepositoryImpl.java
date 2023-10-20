package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.Set;

import grafioschtrader.entities.UDFMetadataSecurity;

public class UDFMetadataSecurityJpaRepositoryImpl implements UDFMetadataSecurityJpaRepositoryCustom {

  

  @Override
  public UDFMetadataSecurity saveOnlyAttributes(final UDFMetadataSecurity uDFMetadataSecurity, final UDFMetadataSecurity existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {
    return null;
  }

  @Override
  public Set<Class<? extends Annotation>> getUpdatePropertyLevels(UDFMetadataSecurity existingEntity) {
    // TODO Auto-generated method stub
    return null;
  }
}
