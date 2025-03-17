package grafioschtrader.repository;

import java.util.List;

import grafiosch.repository.BaseRepositoryCustom;
import grafioschtrader.entities.ImportTransactionPlatform;
import grafioschtrader.platform.IPlatformTransactionImport;

public interface ImportTransactionPlatformJpaRepositoryCustom extends BaseRepositoryCustom<ImportTransactionPlatform> {
  List<IPlatformTransactionImport> getPlatformTransactionImport();
}
