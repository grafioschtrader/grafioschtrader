package grafioschtrader.repository;

import grafiosch.repository.BaseRepositoryCustom;
import grafioschtrader.entities.AlgoTop;

public interface AlgoTopJpaRepositoryCustom extends BaseRepositoryCustom<AlgoTop> {

  /**
   * Normalizes the percentages of all direct children of the given parent node so they sum to exactly 100.00.
   * The parent can be an AlgoTop (children are AlgoAssetclass) or an AlgoAssetclass (children are AlgoSecurity).
   *
   * @param idAlgoAssetclassSecurity the parent node ID
   * @param idTenant the tenant ID for ownership validation
   */
  void normalizeChildPercentages(Integer idAlgoAssetclassSecurity, Integer idTenant);

  /**
   * Normalizes the entire AlgoTop tree: first the AlgoAssetclass children, then the AlgoSecurity children
   * of each AlgoAssetclass. All levels are normalized to sum to exactly 100.00.
   *
   * @param idAlgoAssetclassSecurity the AlgoTop node ID
   * @param idTenant the tenant ID for ownership validation
   */
  void normalizeAllPercentages(Integer idAlgoAssetclassSecurity, Integer idTenant);
}
