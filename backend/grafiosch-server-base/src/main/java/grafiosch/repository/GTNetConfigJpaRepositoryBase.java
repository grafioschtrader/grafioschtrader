package grafiosch.repository;

import org.springframework.data.repository.NoRepositoryBean;

import grafiosch.entities.GTNetConfig;
import grafiosch.rest.UpdateCreateJpaRepository;

/**
 * Base repository interface for GTNetConfig entities used by the library handler infrastructure.
 */
@NoRepositoryBean
public interface GTNetConfigJpaRepositoryBase extends UpdateCreateJpaRepository<GTNetConfig>,
    GTNetConfigJpaRepositoryCustom {
}
