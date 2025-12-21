package grafioschtrader.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.GTNetEntity;

/**
 * Repository for managing GTNetEntity configurations.
 *
 * GTNetEntity represents a data type configuration for a specific GTNet connection.
 * Each GTNet can have multiple GTNetEntity entries (one per data type like LAST_PRICE, HISTORICAL_PRICES).
 */
public interface GTNetEntityJpaRepository extends JpaRepository<GTNetEntity, Integer> {

  /**
   * Finds all entity configurations for a specific GTNet domain.
   *
   * @param idGtNet the GTNet identifier
   * @return list of GTNetEntity entries for the specified GTNet
   */
  List<GTNetEntity> findByIdGtNet(Integer idGtNet);

  /**
   * Finds a specific entity configuration by GTNet and entity kind.
   *
   * @param idGtNet the GTNet identifier
   * @param entityKind the entity kind byte value (0=LAST_PRICE, 1=HISTORICAL_PRICES)
   * @return Optional containing the matching GTNetEntity, or empty if not found
   */
  Optional<GTNetEntity> findByIdGtNetAndEntityKind(Integer idGtNet, byte entityKind);

  /**
   * Finds all entity configurations that accept requests for a specific entity kind.
   *
   * @param entityKind the entity kind byte value
   * @return list of GTNetEntity entries that accept requests for the specified kind
   */
  List<GTNetEntity> findByEntityKindAndAcceptRequestTrue(byte entityKind);
}
