package grafioschtrader.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import grafiosch.rest.UpdateCreateJpaRepository;
import grafioschtrader.entities.ImportTransactionPlatform;

public interface ImportTransactionPlatformJpaRepository extends JpaRepository<ImportTransactionPlatform, Integer>,
    ImportTransactionPlatformJpaRepositoryCustom, UpdateCreateJpaRepository<ImportTransactionPlatform> {

  ImportTransactionPlatform findFristByNameContaining(String name);
}
