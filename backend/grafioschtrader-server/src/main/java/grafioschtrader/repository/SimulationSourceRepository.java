package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import grafioschtrader.entities.*;

/** Tenant-scoped source reads shared by historical allocation and simulation initialization. */
public interface SimulationSourceRepository extends Repository<Tenant, Integer> {
  @Query(name = "SimulationSourceRepository.transactions")
  List<Transaction> transactions(Integer idTenant, LocalDate exclusiveEnd);

  @Query("SELECT MIN(t.transactionDate) FROM Transaction t WHERE t.idTenant = ?1")
  LocalDate firstTransactionDate(Integer idTenant);

  @Query("SELECT p FROM Portfolio p WHERE p.idTenant = ?1 ORDER BY p.idPortfolio")
  List<Portfolio> portfolios(Integer idTenant);

  @Query("SELECT a FROM Securityaccount a WHERE a.idTenant = ?1 ORDER BY a.idSecuritycashAccount")
  List<Securityaccount> securityaccounts(Integer idTenant);

  @Query("SELECT a FROM Cashaccount a WHERE a.idTenant = ?1 ORDER BY a.idSecuritycashAccount")
  List<Cashaccount> cashaccounts(Integer idTenant);
}
