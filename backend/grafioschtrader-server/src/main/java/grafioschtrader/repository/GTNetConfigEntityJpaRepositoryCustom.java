package grafioschtrader.repository;

import grafiosch.repository.BaseRepositoryCustom;
import grafioschtrader.entities.GTNetConfigEntity;

/**
 * Custom repository interface for GTNetConfigEntity operations.
 * Provides attribute-level update functionality for editing entity-specific
 * exchange configuration, logging preferences, and consumer usage priority.
 */
public interface GTNetConfigEntityJpaRepositoryCustom extends BaseRepositoryCustom<GTNetConfigEntity> {
}
