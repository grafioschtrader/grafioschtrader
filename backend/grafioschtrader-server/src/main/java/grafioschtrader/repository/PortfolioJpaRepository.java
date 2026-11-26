package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.rest.UpdateCreateDeleteWithTenantJpaRepository;
import grafioschtrader.entities.Portfolio;

public interface PortfolioJpaRepository extends JpaRepository<Portfolio, Integer>, PortfolioJpaRepositoryCustom,
    UpdateCreateDeleteWithTenantJpaRepository<Portfolio> {

  @Transactional
  @Modifying
  int deleteByIdPortfolioAndIdTenant(Integer idPortfolio, Integer idTenant);

  Portfolio findByName(String name);

  List<Portfolio> findByIdTenantOrderByName(Integer idTenant);

  Portfolio findBySecuritycashaccountList_idSecuritycashAccountAndIdTenant(Integer idSecuritycashAccount,
      Integer idTenant);

  Portfolio findByIdTenantAndIdPortfolio(Integer idTenant, Integer idPortfolio);

  /**
   * Sets the benchmark of a portfolio, restricted to the portfolios of the given tenant. The only write path of the
   * column; a save of the portfolio itself never copies it.
   *
   * @param idTenant    the tenant of the authenticated user
   * @param idPortfolio the portfolio
   * @param idBenchmark the benchmark instrument, null to inherit the benchmark of the tenant again
   * @return the number of updated rows, 0 when the portfolio does not belong to the tenant
   */
  @Modifying(clearAutomatically = true)
  @Query("UPDATE Portfolio p SET p.idBenchmark = ?3 WHERE p.idPortfolio = ?2 AND p.idTenant = ?1")
  int updateIdBenchmark(Integer idTenant, Integer idPortfolio, Integer idBenchmark);

  @Query(nativeQuery = true)
  List<Integer> getExistingTransactionOnSecurityaccount(Integer idTenant);

  /**
   * The accounts of every portfolio of a tenant that overrides the cost basis method of its tenant. Portfolios that
   * follow the tenant are left out, so the result is normally empty. Cash accounts are returned as well, which does
   * no harm because the method is only ever looked up for a security account.
   *
   * Named query: Portfolio.getCostBasisMethodOverrides
   *
   * @param idTenant the tenant whose portfolios are read
   * @return rows of [id_securitycash_account, cost_basis_method], the method as the byte value of
   *         {@link grafioschtrader.types.CostBasisMethod}
   */
  @Query(nativeQuery = true)
  List<Object[]> getCostBasisMethodOverrides(Integer idTenant);
}
