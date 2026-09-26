package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import grafioschtrader.entities.*;

/** Tenant-scoped source reads shared by historical allocation and simulation initialization. */
public interface SimulationSourceRepository extends Repository<Tenant, Integer>, SimulationSourceRepositoryCustom {

  /**
   * Reads the ledger of a tenant straight from the database. Callers use
   * {@link SimulationSourceRepositoryCustom#transactions(Integer, LocalDate)}, which serves the same rows from
   * {@link SimulationLedgerCache} while a replay runs on the calling thread.
   *
   * Named query: SimulationSourceRepository.loadTransactions
   *
   * @param idTenant     the tenant whose transactions are read
   * @param exclusiveEnd the first transaction date that is no longer included
   * @return the transactions ordered by date, time and id, with instrument and cash account fetched in the same query
   */
  @Query(name = "SimulationSourceRepository.loadTransactions")
  List<Transaction> loadTransactions(Integer idTenant, LocalDate exclusiveEnd);

  /**
   * Reads the transactions of a tenant that were written after the given one, regardless of their date. This is how
   * {@link SimulationLedgerCache} catches up with the fills a replay has booked since its last read.
   *
   * Named query: SimulationSourceRepository.loadTransactionsAfterId
   *
   * @param idTenant      the tenant whose transactions are read
   * @param idTransaction the highest id already held; 0 reads the whole ledger
   * @return the newer transactions ordered by date, time and id, with instrument and cash account fetched
   */
  @Query(name = "SimulationSourceRepository.loadTransactionsAfterId")
  List<Transaction> loadTransactionsAfterId(Integer idTenant, Integer idTransaction);

  /**
   * Row count and highest id of the ledger of a tenant. Two ledgers with the same fingerprint hold the same rows as
   * long as rows are only ever inserted with increasing ids or deleted, which is what lets
   * {@link SimulationLedgerCache} detect a rollback, a deletion or a row that became visible late.
   *
   * @param idTenant the tenant whose ledger is described
   * @return count and highest id; the id is null for an empty ledger
   */
  @Query("SELECT COUNT(t) AS rowCount, MAX(t.idTransaction) AS maxIdTransaction FROM Transaction t WHERE t.idTenant = ?1")
  LedgerFingerprint ledgerFingerprint(Integer idTenant);

  @Query("SELECT MIN(t.transactionDate) FROM Transaction t WHERE t.idTenant = ?1")
  LocalDate firstTransactionDate(Integer idTenant);

  @Query("SELECT p FROM Portfolio p WHERE p.idTenant = ?1 ORDER BY p.idPortfolio")
  List<Portfolio> portfolios(Integer idTenant);

  @Query("SELECT a FROM Securityaccount a WHERE a.idTenant = ?1 ORDER BY a.idSecuritycashAccount")
  List<Securityaccount> securityaccounts(Integer idTenant);

  @Query("SELECT a FROM Cashaccount a WHERE a.idTenant = ?1 ORDER BY a.idSecuritycashAccount")
  List<Cashaccount> cashaccounts(Integer idTenant);

  /** Projection of {@link #ledgerFingerprint(Integer)}. */
  interface LedgerFingerprint {
    long getRowCount();

    Integer getMaxIdTransaction();
  }
}
