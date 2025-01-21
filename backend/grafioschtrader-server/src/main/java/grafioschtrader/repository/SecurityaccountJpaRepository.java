package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.common.UpdateQuery;
import grafioschtrader.dto.ITransactionCost;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.rest.UpdateCreateDeleteWithTenantJpaRepository;

public interface SecurityaccountJpaRepository extends JpaRepository<Securityaccount, Integer>,
    SecurityaccountJpaRepositoryCustom, UpdateCreateDeleteWithTenantJpaRepository<Securityaccount> {

  Securityaccount findByIdSecuritycashAccountAndIdTenant(Integer idSecuritycashAccount, Integer idTenant);

  List<Securityaccount> findByIdTenant(Integer idTenant);

  @Query("SELECT s FROM Securityaccount s, Portfolio p WHERE p.name = ?1 AND s.name = ?2")
  Securityaccount findByPortfolioNameAndName(String portfolioName, String name);

  @Transactional
  @Modifying
  void deleteByPortfolio(Portfolio portfolio);

  @Query("Select DISTINCT(s) FROM Securityaccount s, Portfolio p  WHERE p.idTenant = ?1")
  List<Securityaccount> findByIdTentand(Integer idTenant);

  List<Securityaccount> findByPortfolio_IdPortfolioAndIdTenant(Integer idPortfolio, Integer idTenant);

  @UpdateQuery(value = "DELETE FROM Securityaccount s WHERE s.idSecuritycashAccount = ?1 AND s.idTenant = ?2")
  int deleteSecurityaccount(Integer idSecuritycashAccount, Integer idTenant);

  @Query(nativeQuery = true)
  List<ITransactionCost> getAllTransactionCostByTenant(Integer idTenant);

  @Query(nativeQuery = true)
  List<ITransactionCost> getAllTransactionCostBySecurityaccount(Integer idSecurityaccount);
}
