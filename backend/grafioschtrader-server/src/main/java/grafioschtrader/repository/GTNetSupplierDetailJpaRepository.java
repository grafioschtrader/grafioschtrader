package grafioschtrader.repository;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.GTNetSupplierDetail;

public interface GTNetSupplierDetailJpaRepository extends JpaRepository<GTNetSupplierDetail, Integer> {

  @Query(value = "SELECT DISTINCT id_securitycurrency FROM gt_net_supplier_detail WHERE id_securitycurrency IN (?1)", nativeQuery = true)
  Set<Integer> findIdSecuritycurrencyWithDetails(List<Integer> ids);

  /**
   * Deletes all GTNetSupplierDetail entries for a specific GTNet peer.
   * Used during full recreation mode to clear existing entries before creating new ones.
   *
   * @param idGtNet the GTNet peer ID whose supplier details should be deleted
   */
  @Modifying
  @Query("DELETE FROM GTNetSupplierDetail d WHERE d.gtNetConfig.idGtNet = ?1")
  void deleteByIdGtNet(Integer idGtNet);

  /**
   * Finds all GTNetSupplierDetail entries for given instruments and entity kind.
   * Used for filtering instruments when querying AC_OPEN suppliers, ensuring that
   * only instruments a supplier is known to support are sent in requests.
   *
   * @param entityKind the entity kind (0=LAST_PRICE, 1=HISTORICAL_PRICES)
   * @param idSecuritycurrencies list of instrument IDs to check
   * @return list of supplier details matching the criteria
   */
  @Query("SELECT d FROM GTNetSupplierDetail d WHERE d.entityKind = ?1 AND d.securitycurrency.idSecuritycurrency IN ?2")
  List<GTNetSupplierDetail> findByEntityKindAndInstrumentIds(byte entityKind, List<Integer> idSecuritycurrencies);
}
