package grafiosch.repository;

import java.util.List;

import grafiosch.entities.GTNetEntity;

/**
 * Repository for managing GTNetEntity configurations in Grafioschtrader.
 *
 * Extends the base repository with trading-specific query methods for GTNetEntity access patterns
 * like finding entities that accept specific request types.
 *
 * @see GTNetEntityJpaRepositoryBase for base query methods
 */
public interface GTNetEntityJpaRepository extends GTNetEntityJpaRepositoryBase {

  /**
   * Finds all entity configurations that accept requests for a specific entity kind.
   *
   * This method is specific to Grafioschtrader's trading functionality where entities
   * can be configured to accept or reject different types of data exchange requests.
   *
   * @param entityKind the entity kind byte value (0=LAST_PRICE, 1=HISTORICAL_PRICES, etc.)
   * @return list of GTNetEntity entries that accept requests for the specified kind
   */
  List<GTNetEntity> findByEntityKindAndAcceptRequestTrue(byte entityKind);
}
