package grafiosch.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import grafiosch.entities.GTNetMaintenanceWindow;
import jakarta.transaction.Transactional;

/**
 * Repository for the announced maintenance windows of remote GTNet instances.
 *
 * <p>
 * A window is never turned into a persisted server state. The two {@code findIdGtNetIn…} queries are the live
 * evaluation every caller uses before contacting a peer, and they are deliberately answered in one statement for all
 * peers at once, because the delivery and broadcast tasks iterate over the whole peer list.
 * </p>
 *
 * @see GTNetMaintenanceWindow
 */
public interface GTNetMaintenanceWindowJpaRepository extends JpaRepository<GTNetMaintenanceWindow, Integer> {

  /**
   * All windows of one remote, most recent first. Feeds the maintenance panel of the expanded GTNet row.
   *
   * @param idGtNet the remote GTNet ID
   * @return the windows of that remote, ordered by start descending
   */
  List<GTNetMaintenanceWindow> findByIdGtNetOrderByFromDateTimeDesc(Integer idGtNet);

  /**
   * Looks a window up by its natural key, so that a re-delivered announcement updates the existing row instead of
   * adding a duplicate. The same triple carries the unique constraint of the table.
   *
   * @param idGtNet      the remote GTNet ID
   * @param fromDateTime start of the window
   * @param toDateTime   end of the window
   * @return the matching window, if one exists
   */
  Optional<GTNetMaintenanceWindow> findByIdGtNetAndFromDateTimeAndToDateTime(Integer idGtNet,
      LocalDateTime fromDateTime, LocalDateTime toDateTime);

  /**
   * Removes the windows an announcement created. Called when a {@code GT_NET_MAINTENANCE_CANCEL_ALL_C} referencing that
   * announcement is received.
   *
   * @param idGtNetMessage the ID of the received announcement
   */
  @Modifying
  @Transactional
  void deleteByIdGtNetMessage(Integer idGtNetMessage);

  /**
   * The IDs of all remotes that are inside one of their announced maintenance windows right now. Callers use it as an
   * exclusion set rather than asking per peer.
   *
   * @param now the moment to test, normally {@code LocalDateTime.now()}
   * @return the IDs of the remotes currently under maintenance, empty when none is
   */
  @Query("SELECT DISTINCT w.idGtNet FROM GTNetMaintenanceWindow w "
      + "WHERE w.fromDateTime <= ?1 AND w.toDateTime >= ?1")
  List<Integer> findIdGtNetInMaintenance(LocalDateTime now);

  /**
   * Counts the announced windows per remote, for the header of the maintenance panel. The count includes windows that
   * have already passed, because they stay visible until their announcement is deleted.
   *
   * @return one row per remote that has at least one window, {@code [idGtNet, count]}
   */
  @Query("SELECT w.idGtNet, COUNT(w) FROM GTNetMaintenanceWindow w GROUP BY w.idGtNet")
  List<Object[]> countPerGtNet();

}
