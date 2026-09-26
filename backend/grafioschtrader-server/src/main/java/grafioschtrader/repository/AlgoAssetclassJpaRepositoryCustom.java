package grafioschtrader.repository;

import grafiosch.repository.BaseRepositoryCustom;
import grafioschtrader.entities.AlgoAssetclass;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyLists;
import grafioschtrader.search.SecuritycurrencySearch;

public interface AlgoAssetclassJpaRepositoryCustom extends BaseRepositoryCustom<AlgoAssetclass> {

  /**
   * Searches the instruments that may still be added to a custom category, independent of the watchlist of the
   * hierarchy. Instruments already assigned to the category are left out, as are those a simulation cannot trade (CFD,
   * Forex, a leverage factor other than 1) and those that ended before the hierarchy opens.
   *
   * @param idAlgoAssetclassSecurity the custom category, which must belong to the main tenant of the user
   * @param securitycurrencySearch   the search criteria entered in the search dialog
   * @return the matching securities; the currency pair list is always empty because a hierarchy holds no currency pairs
   */
  SecuritycurrencyLists searchByCriteria(Integer idAlgoAssetclassSecurity,
      SecuritycurrencySearch securitycurrencySearch);

  /**
   * Adds the given securities as instrument nodes to a custom category. Each node passes the same validation as one
   * created in the security dialog. A new node gets the mean weight of its existing siblings, or an equal share when
   * the category is empty; afterwards the weights of the category are normalized to 100, so the existing nodes keep
   * their proportions among each other. Securities already assigned to the category are skipped.
   *
   * @param idAlgoAssetclassSecurity the custom category, which must belong to the main tenant of the user
   * @param securitycurrencyLists    the securities to add; only their ids are read, currency pairs are ignored
   * @return the custom category after the addition
   */
  AlgoAssetclass addSecuritiesToCustomCategory(Integer idAlgoAssetclassSecurity,
      SecuritycurrencyLists securitycurrencyLists) throws Exception;

}
