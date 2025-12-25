package grafioschtrader.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.GTNetConfigEntity;

/**
 * Repository for managing GTNetConfigEntity configurations.
 *
 * GTNetConfigEntity stores entity-specific exchange configuration for a GTNet connection.
 * Each GTNetEntity can have one GTNetConfigEntity containing exchange settings, logging preferences,
 * and consumer usage priority. The primary key is shared with GTNetEntity (id_gt_net_entity).
 */
public interface GTNetConfigEntityJpaRepository extends JpaRepository<GTNetConfigEntity, Integer> {

  /**
   * Finds the configuration entity for a specific GTNetEntity.
   * Since id_gt_net_entity is the primary key, this is equivalent to findById().
   *
   * @param idGtNetEntity the GTNetEntity identifier (also the primary key)
   * @return Optional containing the matching GTNetConfigEntity, or empty if not found
   */
  Optional<GTNetConfigEntity> findByIdGtNetEntity(Integer idGtNetEntity);
}
