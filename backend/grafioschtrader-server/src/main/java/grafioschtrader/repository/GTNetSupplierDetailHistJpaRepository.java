package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.GTNetSupplierDetailHist;

public interface GTNetSupplierDetailHistJpaRepository extends JpaRepository<GTNetSupplierDetailHist, Integer> {

  List<GTNetSupplierDetailHist> findByIdGtNetSupplierDetailIn(List<Integer> ids);

  /**
   * Loads the OHL quality percentage each supplier reported for the given instruments. The join to
   * gt_net_supplier_detail is needed because the peer and the instrument are held there, while the quality metric lives
   * in the child table of this repository. This split is also why the query cannot be added to the library repository
   * GTNetSupplierDetailJpaRepository: grafiosch-base must not reference grafioschtrader entities.
   *
   * <p>
   * Rows whose ohl_percentage is NULL are omitted rather than returned as null values, so a caller can treat "absent"
   * and "unknown" identically. Currency pairs are always absent by design - their quality metric is never populated
   * because their OHLC content is provider dependent and mostly close-only.
   * </p>
   *
   * Named query: GTNetSupplierDetailHist.findOhlPercentagesByEntityKindAndInstrumentIds
   *
   * @param entityKind the entity kind to restrict to (0 = LAST_PRICE, 1 = HISTORICAL_PRICES)
   * @param idEntities the instrument IDs being requested
   * @return rows of [idGtNet, idEntity, ohlPercentage], never containing a null percentage
   */
  @Query(nativeQuery = true)
  List<Object[]> findOhlPercentagesByEntityKindAndInstrumentIds(byte entityKind, List<Integer> idEntities);
}
