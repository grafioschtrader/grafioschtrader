package grafioschtrader.repository;

import java.util.List;

import grafiosch.repository.BaseRepositoryCustom;
import grafioschtrader.entities.Portfolio;

public interface PortfolioJpaRepositoryCustom extends BaseRepositoryCustom<Portfolio> {

  /**
   * Remove all cash accounts of this Portfolio
   */
  Portfolio removeCashaccounts(Integer idPortfolio);

  /**
   * Remove all security accounts of this portfolio
   */
  Portfolio removeSecurityaccounts(Integer idPortfolio);

  List<Portfolio> setExistingTransactionOnSecurityaccount(Integer idTenant);

  int delEntityWithTenant(Integer id, Integer idTenant);

  /**
   * If the currency of a portfolio has been changed, currency pairs may have to
   * be created according to the transactions of this portfolio and bank accounts.
   *
   * @param idPortfolio ID des portfolios
   * @return ID of tenant
   */
  Integer createNotExistingCurrencypairs(Integer idPortfolio);

}
