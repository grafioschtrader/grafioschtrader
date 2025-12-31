package grafioschtrader.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import grafiosch.rest.UpdateCreateJpaRepository;
import grafioschtrader.entities.GTNetConfigEntity;

/**
 * Repository for managing GTNetConfigEntity configurations.
 *
 * GTNetConfigEntity stores entity-specific exchange configuration for a GTNet connection.
 * Each GTNetEntity can have one GTNetConfigEntity containing exchange settings, logging preferences,
 * and consumer usage priority. The primary key is shared with GTNetEntity (id_gt_net_entity).
 *
 * Only useDetailLog and consumerUsage fields can be updated by the administrator.
 * The entity is created automatically when an exchange request is accepted (GT_NET_UPDATE_SERVERLIST_ACCEPT_S).
 */
public interface GTNetConfigEntityJpaRepository extends JpaRepository<GTNetConfigEntity, Integer>,
    GTNetConfigEntityJpaRepositoryCustom, UpdateCreateJpaRepository<GTNetConfigEntity> {

  /**
   * Finds the configuration entity for a specific GTNetEntity.
   * Since id_gt_net_entity is the primary key, this is equivalent to findById().
   *
   * @param idGtNetEntity the GTNetEntity identifier (also the primary key)
   * @return Optional containing the matching GTNetConfigEntity, or empty if not found
   */
  Optional<GTNetConfigEntity> findByIdGtNetEntity(Integer idGtNetEntity);
}
