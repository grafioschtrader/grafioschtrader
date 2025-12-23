package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import grafiosch.rest.UpdateCreateJpaRepository;
import grafioschtrader.entities.GTNet;

/**
 * Repository for managing GTNet domain configurations.
 *
 * Provides CRUD operations for remote domain entries plus specialized queries for:
 * <ul>
 *   <li>Finding broadcast targets (domains with any data exchange configured)</li>
 *   <li>Finding active price providers (for consumer-side queries)</li>
 *   <li>Looking up domains by URL (for handshake processing)</li>
 * </ul>
 *
 * @see GTNetJpaRepositoryCustom for complex operations like message submission
 */
public interface GTNetJpaRepository
    extends JpaRepository<GTNet, Integer>, GTNetJpaRepositoryCustom, UpdateCreateJpaRepository<GTNet> {

  /**
   * Finds domains that have at least one GTNetEntity accepting requests.
   * Used to determine broadcast targets for maintenance announcements and shutdown notices.
   * Checks for acceptRequest > 0 (AC_OPEN=1 or AC_PUSH_OPEN=2).
   *
   * @return list of domains where at least one entity kind accepts requests
   */
  @Query("SELECT DISTINCT g FROM GTNet g JOIN g.gtNetEntities e WHERE e.acceptRequest > 0")
  List<GTNet> findByAnyAcceptRequest();

  /**
   * Finds domains that have a LAST_PRICE entity with specific consumer usage and server state.
   * Used to find active price providers when this instance acts as a consumer.
   *
   * @param consumerUsage the priority level (0 = not used, higher = used with priority)
   * @param serverState the required server state (typically SS_OPEN = 4)
   * @return list of domains matching the criteria for LAST_PRICE entity kind
   */
  @Query("SELECT DISTINCT g FROM GTNet g JOIN g.gtNetEntities e JOIN e.gtNetConfigEntity c " +
         "WHERE e.entityKind = 0 AND c.consumerUsage = :consumerUsage AND e.serverState = :serverState")
  List<GTNet> findByLastpriceConsumerUsageAndServerState(
      @Param("consumerUsage") byte consumerUsage,
      @Param("serverState") byte serverState);

  /**
   * Looks up a domain by its URL.
   * Used during handshake processing to check if a remote domain is already known,
   * and to find the matching entry when receiving M2M requests.
   *
   * @param domainRemoteName the full base URL (e.g., "https://example.com:8080")
   * @return the matching GTNet entry, or null if not found
   */
  GTNet findByDomainRemoteName(String domainRemoteName);

  /**
   * Finds GTNet entries that have any data exchange configured.
   * Returns entries where at least one GTNetEntity has a GTNetConfigEntity with exchange > 0.
   * Used to determine which remote instances should be notified about this server's online/offline status.
   *
   * @return list of GTNet entries with configured data exchange
   */
  @Query("SELECT DISTINCT g FROM GTNet g JOIN g.gtNetEntities e JOIN e.gtNetConfigEntity c WHERE c.exchange > 0")
  List<GTNet> findWithConfiguredExchange();

}
