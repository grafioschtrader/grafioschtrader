package grafioschtrader.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.ImportTransactionPlatform;
import grafioschtrader.rest.UpdateCreateJpaRepository;

public interface ImportTransactionPlatformJpaRepository extends JpaRepository<ImportTransactionPlatform, Integer>,
    ImportTransactionPlatformJpaRepositoryCustom, UpdateCreateJpaRepository<ImportTransactionPlatform> {

  ImportTransactionPlatform findFristByNameContaining(String name);
}
