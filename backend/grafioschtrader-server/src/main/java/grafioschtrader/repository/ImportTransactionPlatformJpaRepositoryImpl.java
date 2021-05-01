package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;

import grafioschtrader.entities.ImportTransactionPlatform;
import grafioschtrader.platform.IPlatformTransactionImport;

public class ImportTransactionPlatformJpaRepositoryImpl extends BaseRepositoryImpl<ImportTransactionPlatform>
    implements ImportTransactionPlatformJpaRepositoryCustom {

  @Autowired
  ImportTransactionPlatformJpaRepository importTransactionPlatformJpaRepository;

  @Autowired(required = false)
  public List<IPlatformTransactionImport> platformTransactionImportList = new ArrayList<>();

  @Override
  public ImportTransactionPlatform saveOnlyAttributes(ImportTransactionPlatform importTransactionPlatform,
      ImportTransactionPlatform existingEntity, final Set<Class<? extends Annotation>> updatePropertyLevelClasses) {
    return importTransactionPlatformJpaRepository.save(importTransactionPlatform);
  }

  @Override
  public List<IPlatformTransactionImport> getPlatformTransactionImport() {
    return this.platformTransactionImportList;
  }

}
