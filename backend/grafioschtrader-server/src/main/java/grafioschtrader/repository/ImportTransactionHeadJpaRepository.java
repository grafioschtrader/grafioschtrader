package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.ImportTransactionHead;
import grafioschtrader.rest.UpdateCreateDeleteWithTenantJpaRepository;

public interface ImportTransactionHeadJpaRepository extends JpaRepository<ImportTransactionHead, Integer>,
    ImportTransactionHeadJpaRepositoryCustom, UpdateCreateDeleteWithTenantJpaRepository<ImportTransactionHead> {

  List<ImportTransactionHead> findBySecurityaccount_idSecuritycashAccountAndIdTenant(Integer idSecuritycashaccount,
      Integer idTenant);

  ImportTransactionHead findByIdTransactionHeadAndIdTenant(Integer idTransactionHead, Integer idTenant);

  @Transactional
  @Modifying
  int deleteByIdTransactionHeadAndIdTenant(Integer idTransactionHead, Integer idTenant);
}
