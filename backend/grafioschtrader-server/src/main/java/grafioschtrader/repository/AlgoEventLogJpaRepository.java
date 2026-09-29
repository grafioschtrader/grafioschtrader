package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.AlgoEventLog;

/**
 * The audit trail of a historical replay. Written by the replay service only and read page by page or as a bounded
 * window around a day, because a run over a long horizon writes one entry per decision and trading day and the whole
 * trail is far too large for one response.
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

  /**
   * The older half of a window of the trail: the entries before the anchor day, nearest to it first, so that the limit
   * cuts off the far end of the run rather than the days next to the anchor.
   *
   * @param idSimulationResult the run whose trail is read
   * @param anchorDate         first day that is no longer part of this half
   * @param limit              maximum number of entries
   * @return the entries before the anchor day, newest first
   */
  List<AlgoEventLog> findByIdSimulationResultAndEventDateLessThanOrderByEventDateDescIdAlgoEventDesc(
      Integer idSimulationResult, LocalDate anchorDate, Limit limit);

  /**
   * The newer half of a window of the trail: the entries on and after the anchor day, nearest to it first.
   *
   * @param idSimulationResult the run whose trail is read
   * @param anchorDate         first day of this half
   * @param limit              maximum number of entries
   * @return the entries from the anchor day on, oldest first
   */
  List<AlgoEventLog> findByIdSimulationResultAndEventDateGreaterThanEqualOrderByEventDateAscIdAlgoEventAsc(
      Integer idSimulationResult, LocalDate anchorDate, Limit limit);

  long countByIdSimulationResult(Integer idSimulationResult);

  long countByIdTenant(Integer idTenant);

  void deleteByIdTenant(Integer idTenant);
}
