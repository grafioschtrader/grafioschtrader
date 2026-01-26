package grafiosch.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import grafiosch.entities.GTNetEntity;

/**
 * Base repository interface for GTNetEntity CRUD operations.
 *
 * Provides common query methods for managing GTNetEntity configurations. These entities
 * represent data type configurations (e.g., LAST_PRICE, HISTORICAL_PRICES) for specific
 * GTNet connections.
 *
 * <p>Application modules should extend this interface to add application-specific queries.</p>
 */
@NoRepositoryBean
public interface GTNetEntityJpaRepositoryBase extends JpaRepository<GTNetEntity, Integer> {

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
   * @param entityKind the entity kind byte value (e.g., 0=LAST_PRICE, 1=HISTORICAL_PRICES)
   * @return Optional containing the matching GTNetEntity, or empty if not found
   */
  Optional<GTNetEntity> findByIdGtNetAndEntityKind(Integer idGtNet, byte entityKind);
}
