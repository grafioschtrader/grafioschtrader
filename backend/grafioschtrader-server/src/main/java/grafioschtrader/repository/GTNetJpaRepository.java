package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import grafiosch.rest.UpdateCreateJpaRepository;
import grafioschtrader.entities.GTNet;

/**
 * Repository for managing GTNet domain configurations.
 *
 * Provides CRUD operations for remote domain entries plus specialized queries for:
 * <ul>
 *   <li>Finding broadcast targets (domains accepting entity or price requests)</li>
 *   <li>Finding active price providers (for consumer-side queries)</li>
 *   <li>Looking up domains by URL (for handshake processing)</li>
 * </ul>
 *
 * @see GTNetJpaRepositoryCustom for complex operations like message submission
 */
public interface GTNetJpaRepository
    extends JpaRepository<GTNet, Integer>, GTNetJpaRepositoryCustom, UpdateCreateJpaRepository<GTNet> {

  /**
   * Finds domains that accept either entity or lastprice requests.
   * Used to determine broadcast targets for maintenance announcements and shutdown notices.
   *
   * @param acceptEntityRequest true to include domains accepting entity requests
   * @param acceptLastpriceRequest true to include domains accepting lastprice requests
   * @return list of domains matching either criterion
   */
  List<GTNet> findByAcceptEntityRequestOrAcceptLastpriceRequest(boolean acceptEntityRequest,
      boolean acceptLastpriceRequest);

  /**
   * Finds domains that match both the given consumer usage priority and server state.
   * Used to find active price providers when this instance acts as a consumer.
   *
   * Note: The parameter names suggest a bug - lastpriceConsumerUsage should probably be "greater than"
   * rather than "equals", and the query should find providers with SS_OPEN state.
   *
   * @param lastpriceConsumerUsage the priority level (0 = not used, higher = used with priority)
   * @param lastpriceServerState the required server state (typically SS_OPEN = 4)
   * @return list of domains matching both criteria
   */
  List<GTNet> findByLastpriceConsumerUsageAndLastpriceServerState(byte lastpriceConsumerUsage,
      byte lastpriceServerState);

  /**
   * Looks up a domain by its URL.
   * Used during handshake processing to check if a remote domain is already known,
   * and to find the matching entry when receiving M2M requests.
   *
   * @param domainRemoteName the full base URL (e.g., "https://example.com:8080")
   * @return the matching GTNet entry, or null if not found
   */
  GTNet findByDomainRemoteName(String domainRemoteName);

}
