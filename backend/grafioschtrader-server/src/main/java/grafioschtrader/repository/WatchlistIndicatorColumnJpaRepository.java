package grafioschtrader.repository;

import java.util.*;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.rest.UpdateCreateDeleteWithTenantJpaRepository;
import grafioschtrader.entities.WatchlistIndicatorColumn;

/** Tenant-scoped ordered definitions, shared by reports and the editor. */
public interface WatchlistIndicatorColumnJpaRepository extends JpaRepository<WatchlistIndicatorColumn, Integer>,
    WatchlistIndicatorColumnJpaRepositoryCustom, UpdateCreateDeleteWithTenantJpaRepository<WatchlistIndicatorColumn> {
  List<WatchlistIndicatorColumn> findByIdTenantOrderByUiOrder(Integer idTenant);

  Optional<WatchlistIndicatorColumn> findByIdWatchlistIndicatorColumnAndIdTenant(Integer id, Integer idTenant);

  long countByIdTenant(Integer idTenant);

  /**
   * Deletes the indicator columns of the tenant that are assigned to the given watchlist and to no other one. Called
   * before that watchlist is deleted: the cascade of the join table removes only the assignment, which would leave such
   * a column without any watchlist and thereby show it on all watchlists. Columns assigned to further watchlists keep
   * those; columns without assignment apply to all watchlists anyway and stay.
   *
   * Named query: WatchlistIndicatorColumn.deleteOnlyAssignedToWatchlist Parameters in SQL: - ?1 - the tenant owning the
   * columns - ?2 - the watchlist about to be deleted
   *
   * @param idTenant    the tenant identifier
   * @param idWatchlist the watchlist about to be deleted
   * @return the number of deleted indicator columns
   */
  @Transactional
  @Modifying
  @Query(nativeQuery = true)
  int deleteOnlyAssignedToWatchlist(Integer idTenant, Integer idWatchlist);
}
