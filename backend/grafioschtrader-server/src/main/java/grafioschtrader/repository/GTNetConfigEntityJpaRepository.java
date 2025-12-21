package grafioschtrader.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.GTNetConfigEntity;

/**
 * Repository for managing GTNetConfigEntity configurations.
 *
 * GTNetConfigEntity stores entity-specific exchange configuration for a GTNet connection.
 * Each GTNetEntity can have one GTNetConfigEntity containing exchange settings, logging preferences,
 * and consumer usage priority.
 */
public interface GTNetConfigEntityJpaRepository extends JpaRepository<GTNetConfigEntity, Integer> {

  /**
   * Finds the configuration entity for a specific GTNetEntity.
   *
   * @param idGtNetEntity the GTNetEntity identifier
   * @return Optional containing the matching GTNetConfigEntity, or empty if not found
   */
  Optional<GTNetConfigEntity> findByIdGtNetEntity(Integer idGtNetEntity);

  /**
   * Finds all configuration entities for a specific GTNetConfig.
   *
   * @param idGtNetConfig the GTNetConfig identifier
   * @return list of GTNetConfigEntity entries for the specified GTNetConfig
   */
  List<GTNetConfigEntity> findByIdGtNetConfig(Integer idGtNetConfig);
}
