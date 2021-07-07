package grafioschtrader.repository;

import java.util.List;

import grafioschtrader.entities.Portfolio;

public interface PortfolioJpaRepositoryCustom extends BaseRepositoryCustom<Portfolio> {

  /**
   * Remove all cash accounts of this Portfolio
   *
   * @param idPortfolio
   * @return
   */
  Portfolio removeCashaccounts(Integer idPortfolio);

  /**
   * Remove all security accounts of this portfolio
   *
   * @param idPortfolio
   * @return
   */
  Portfolio removeSecurityaccounts(Integer idPortfolio);

  List<Portfolio> setExistingTransactionOnSecurityaccount(Integer idTenant);

  int delEntityWithTenant(Integer id, Integer idTenant);

  Integer createNotExistingCurrencypairs(Integer idPortfolio);

}
