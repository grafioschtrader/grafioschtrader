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

  @Query(nativeQuery = true)
  List<Integer> getExistingTransactionOnSecurityaccount(Integer idTenant);
}
