package grafioschtrader.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.rest.UpdateCreateDeleteWithTenantJpaRepository;
import grafioschtrader.entities.ImportTransactionHead;

public interface ImportTransactionHeadJpaRepository extends JpaRepository<ImportTransactionHead, Integer>,
    ImportTransactionHeadJpaRepositoryCustom, UpdateCreateDeleteWithTenantJpaRepository<ImportTransactionHead> {

  List<ImportTransactionHead> findBySecurityaccount_idSecuritycashAccountAndIdTenant(Integer idSecuritycashaccount,
      Integer idTenant);

  ImportTransactionHead findByIdTransactionHeadAndIdTenant(Integer idTransactionHead, Integer idTenant);

  @Transactional
  @Modifying
  int deleteByIdTransactionHeadAndIdTenant(Integer idTransactionHead, Integer idTenant);

  /**
   * Counts the transactions of a tenant dated at or after a point in time that were not created by the given import
   * head. The rollback of an import is only allowed when this count is zero: then no other transaction can depend on
   * one of the imported transactions, and deleting them restores the state the tenant had before the import.
   *
   * Named query: ImportTransactionHead.countForeignTransactionsFromTime
   *
   * @param idTenant          the tenant whose transactions are counted
   * @param fromTime          inclusive lower bound on {@code transaction_time}, normally the start of the day of the
   *                          earliest imported transaction
   * @param idTransactionHead the import head whose own transactions, linked through {@code imp_trans_pos}, are not
   *                          counted
   * @return the number of other transactions of the tenant dated at or after {@code fromTime}
   */
  @Query(nativeQuery = true)
  long countForeignTransactionsFromTime(Integer idTenant, LocalDateTime fromTime, Integer idTransactionHead);
}
