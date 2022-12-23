package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.Cashaccount;
import grafioschtrader.rest.UpdateCreateJpaRepository;

public interface CashaccountJpaRepository extends JpaRepository<Cashaccount, Integer>, CashaccountJpaRepositoryCustom,
    UpdateCreateJpaRepository<Cashaccount> {

  Cashaccount findByIdSecuritycashAccountAndIdTenant(Integer idSecuritycashAccount, Integer idTenant);

  // Missing portfolio1_.id_portfolio = cashaccoun0_1_.id_portfolio
  // @Query("SELECT c FROM Cashaccount c, Portfolio p WHERE p.name = ?1 AND c.name
  // = ?2")

  @Query(value = "SELECT c.id_securitycash_account, s.name, s.note, s.id_portfolio, c.start_amount, c.balance, c.currency "
      + "FROM cashaccount c, securitycashaccount s, portfolio p WHERE c.id_securitycash_account = s.id_securitycash_account "
      + "AND s.id_portfolio = p.id_portfolio AND p.name=?1 AND s.name=?2", nativeQuery = true)
  Cashaccount findByPortfolioNameAndName(String portfolioName, String name);

  // TODO A cashaccount can have many accounts for a single currency
  List<Cashaccount> findByPortfolio_NameAndCurrency(String portfolioName, String currency);

  List<Cashaccount> findByPortfolio_IdPortfolio(Integer idPortfolio);

  // @Query("SELECT COUNT(c) from Cashaccount c WHERE c.idTenant = ?1")
  int countByIdTenant(Integer idTenant);

  @Transactional
  @Modifying
  @Query(value = "DELETE FROM Cashaccount c WHERE c.idSecuritycashAccount = ?1 AND c.idTenant = ?2")
  int deleteCashaccount(Integer idSecuritycashAccount, Integer idTenant);

}
