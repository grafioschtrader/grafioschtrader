package grafiosch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import grafiosch.entities.GTNetConfig;

/**
 * Base repository interface for GTNetConfig entities used by the library handler infrastructure.
 */
@NoRepositoryBean
public interface GTNetConfigJpaRepositoryBase extends JpaRepository<GTNetConfig, Integer> {
}
