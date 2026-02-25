package grafioschtrader.repository;

import java.util.List;
import java.util.Optional;

import grafiosch.rest.UpdateCreateJpaRepository;
import grafioschtrader.entities.GenericConnectorDef;

/**
 * Repository for managing generic feed connector definitions. All active connectors are loaded at startup to create
 * GenericFeedConnector instances that register as Spring beans.
 */
public interface GenericConnectorDefJpaRepository
    extends GenericConnectorDefJpaRepositoryCustom, UpdateCreateJpaRepository<GenericConnectorDef> {

  List<GenericConnectorDef> findByActivatedTrue();

  Optional<GenericConnectorDef> findByShortId(String shortId);
}
