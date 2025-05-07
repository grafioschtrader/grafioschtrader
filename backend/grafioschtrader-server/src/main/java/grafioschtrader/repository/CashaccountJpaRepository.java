package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import grafiosch.common.UpdateQuery;
import grafiosch.rest.UpdateCreateDeleteWithTenantJpaRepository;
import grafioschtrader.entities.Cashaccount;

public interface CashaccountJpaRepository extends JpaRepository<Cashaccount, Integer>, CashaccountJpaRepositoryCustom,
 UpdateCreateDeleteWithTenantJpaRepository<Cashaccount> {

  Cashaccount findByIdSecuritycashAccountAndIdTenant(Integer idSecuritycashAccount, Integer idTenant);

  // TODO A cashaccount can have many accounts for a single currency
  List<Cashaccount> findByPortfolio_NameAndCurrency(String portfolioName, String currency);

  List<Cashaccount> findByPortfolio_IdPortfolio(Integer idPortfolio);

  int countByIdTenant(Integer idTenant);
 
  @UpdateQuery(value = "DELETE FROM Cashaccount c WHERE c.idSecuritycashAccount = ?1 AND c.idTenant = ?2")
  int deleteCashaccount(Integer idSecuritycashAccount, Integer idTenant);

  
//  @Query(nativeQuery = true)
//  Cashaccount findByPortfolioNameAndName(String portfolioName, String name);
}
