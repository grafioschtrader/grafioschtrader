package grafioschtrader.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.AlgoEventLog;

/**
 * The audit trail of a historical replay. Written by the replay service only and read page by page, because a run over
 * a long horizon writes one entry per decision and trading day and the whole trail is far too large for one response.
 */
public interface AlgoEventLogJpaRepository extends JpaRepository<AlgoEventLog, Integer> {

  /**
   * One page of the trail of a run, newest day first, so that the reader sees where a run ended before scrolling back
   * to how it got there.
   *
   * @param idSimulationResult the run whose trail is read
   * @param pageable           page and size, supplied by the REST layer
   * @return the requested page, empty when the run wrote nothing
   */
  Page<AlgoEventLog> findByIdSimulationResultOrderByEventDateDescIdAlgoEventDesc(Integer idSimulationResult,
      Pageable pageable);

  long countByIdTenant(Integer idTenant);

  void deleteByIdTenant(Integer idTenant);
}
