package grafioschtrader.repository;

import java.util.*;

import org.springframework.data.jpa.repository.JpaRepository;

import grafiosch.rest.UpdateCreateDeleteWithTenantJpaRepository;
import grafioschtrader.entities.WatchlistIndicatorColumn;

/** Tenant-scoped ordered definitions, shared by reports and the editor. */
public interface WatchlistIndicatorColumnJpaRepository extends JpaRepository<WatchlistIndicatorColumn, Integer>,
    WatchlistIndicatorColumnJpaRepositoryCustom, UpdateCreateDeleteWithTenantJpaRepository<WatchlistIndicatorColumn> {
  List<WatchlistIndicatorColumn> findByIdTenantOrderByUiOrder(Integer idTenant);

  Optional<WatchlistIndicatorColumn> findByIdWatchlistIndicatorColumnAndIdTenant(Integer id, Integer idTenant);

  long countByIdTenant(Integer idTenant);
}
