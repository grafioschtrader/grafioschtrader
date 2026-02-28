package grafioschtrader.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.GenericConnectorEndpoint;

/**
 * Repository for marking generic connector endpoints as successfully used and transferring ownership when all
 * endpoints of a connector have been used.
 */
public interface GenericConnectorEndpointJpaRepository extends JpaRepository<GenericConnectorEndpoint, Integer> {

  /**
   * Sets the ever_used_successfully flag on the matching endpoint. Only updates rows where the flag is still 0.
   *
   * @param idGenericConnector connector ID
   * @param feedSupport        feed type: 'FS_HISTORY' or 'FS_INTRA'
   * @param instrumentType     instrument type: 'SECURITY' or 'CURRENCY'
   * @return number of rows updated (0 or 1)
   */
  @Transactional
  @Modifying
  @Query(nativeQuery = true, value = "UPDATE generic_connector_endpoint SET ever_used_successfully = 1 "
      + "WHERE id_generic_connector = ?1 AND feed_support = ?2 AND instrument_type = ?3 AND ever_used_successfully = 0")
  int markEndpointUsedSuccessfully(Integer idGenericConnector, String feedSupport, String instrumentType);

  /**
   * Sets created_by to 0 (system ownership) on the connector definition when ALL of its endpoints have been
   * successfully used. This prevents any non-admin user from editing the connector.
   *
   * @param idGenericConnector connector ID
   * @return number of rows updated (0 or 1)
   */
  @Transactional
  @Modifying
  @Query(nativeQuery = true, value = "UPDATE generic_connector_def SET created_by = 0 "
      + "WHERE id_generic_connector = ?1 "
      + "AND NOT EXISTS (SELECT 1 FROM generic_connector_endpoint "
      + "WHERE id_generic_connector = ?1 AND ever_used_successfully = 0)")
  int transferOwnershipIfAllEndpointsUsed(Integer idGenericConnector);
}
