package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import grafiosch.common.UpdateQuery;
import grafiosch.rest.UpdateCreateDeleteWithTenantJpaRepository;
import grafioschtrader.entities.Cashaccount;

public interface CashaccountJpaRepository extends JpaRepository<Cashaccount, Integer>, CashaccountJpaRepositoryCustom,
    UpdateCreateDeleteWithTenantJpaRepository<Cashaccount> {

  Cashaccount findByIdSecuritycashAccountAndIdTenant(Integer idSecuritycashAccount, Integer idTenant);

  List<Cashaccount> findByPortfolio_IdPortfolio(Integer idPortfolio);

  /**
   * Finds the cash accounts of a portfolio in one currency which are assigned to a certain securities account. The
   * portfolio evaluation books the securities result of such a currency/securities account pair on exactly one cash
   * account, so a second one of the pair would receive the same result again.
   *
   * @param idPortfolio              the portfolio of the cash accounts
   * @param currency                 the ISO currency code of the cash accounts
   * @param connectIdSecurityaccount the ID of the assigned securities account
   * @return the cash accounts holding this assignment, in no particular order
   */
  List<Cashaccount> findByPortfolio_IdPortfolioAndCurrencyAndConnectIdSecurityaccount(Integer idPortfolio,
      String currency, Integer connectIdSecurityaccount);

  int countByIdTenant(Integer idTenant);

  @UpdateQuery(value = "DELETE FROM Cashaccount c WHERE c.idSecuritycashAccount = ?1 AND c.idTenant = ?2")
  int deleteCashaccount(Integer idSecuritycashAccount, Integer idTenant);
}
