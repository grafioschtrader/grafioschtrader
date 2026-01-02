package grafioschtrader.task.exec;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafiosch.entities.TaskDataChange;
import grafiosch.exceptions.TaskBackgroundException;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafioschtrader.entities.GTNet;
import grafioschtrader.repository.GTNetJpaRepository;
import grafioschtrader.service.GTNetExchangeSyncService;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.TaskTypeExtended;

/**
 * Task that synchronizes GTNetExchange configurations with GTNet peers.
 *
 * This task is triggered by the frontend when the user saves changes to GTNetExchange configurations. It iterates
 * through all accessible suppliers (both AC_OPEN and AC_PUSH_OPEN) and synchronizes the exchange configuration
 * with each one.
 *
 * <h3>Flow</h3>
 * <ol>
 * <li>Get timestamp from globalparameters indicating last sync time</li>
 * <li>Find all accessible peers (AC_OPEN and AC_PUSH_OPEN) configured for data exchange</li>
 * <li>For each peer, send changed GTNetExchange items and receive their changes</li>
 * <li>Update local GTNetSupplierDetail based on received data</li>
 * <li>Update the sync timestamp in globalparameters</li>
 * </ol>
 *
 * @see GTNetExchangeSyncService for the core sync logic
 */
@Component
public class GTNetExchangeSyncTask implements ITask {

  private static final Logger log = LoggerFactory.getLogger(GTNetExchangeSyncTask.class);

  @Autowired
  private GlobalparametersService globalparametersService;

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  private GTNetExchangeSyncService exchangeSyncService;

  @Override
  public ITaskType getTaskType() {
    return TaskTypeExtended.GTNET_EXCHANGE_SYNC;
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException {
    if (!globalparametersService.isGTNetEnabled()) {
      log.debug("GTNet is disabled, skipping exchange sync");
      return;
    }

    Date lastSyncTimestamp = globalparametersService.getGTNetExchangeSyncTimestamp();
    log.info("Starting GTNet exchange sync (last sync: {})", lastSyncTimestamp);

    // Get all accessible suppliers (both AC_OPEN and AC_PUSH_OPEN)
    List<GTNet> allSuppliers = getAllAccessibleSuppliers();

    if (allSuppliers.isEmpty()) {
      log.info("No accessible peers configured for exchange sync");
      globalparametersService.updateGTNetExchangeSyncTimestamp();
      return;
    }

    int successCount = 0;
    int failCount = 0;

    for (GTNet peer : allSuppliers) {
      try {
        boolean success = exchangeSyncService.syncWithPeer(peer, lastSyncTimestamp);
        if (success) {
          successCount++;
        } else {
          failCount++;
        }
      } catch (Exception e) {
        log.warn("Failed to sync exchange config with {}: {}",
            peer.getDomainRemoteName(), e.getMessage());
        failCount++;
      }
    }

    // Update timestamp after job completion
    globalparametersService.updateGTNetExchangeSyncTimestamp();
    log.info("GTNet exchange sync completed: {} successful, {} failed out of {} peers",
        successCount, failCount, allSuppliers.size());
  }

  /**
   * Returns all accessible suppliers for this instance, including both AC_OPEN and AC_PUSH_OPEN peers.
   * Duplicates are removed in case a peer appears in both lists.
   *
   * @return combined list of all accessible suppliers
   */
  private List<GTNet> getAllAccessibleSuppliers() {
    List<GTNet> pushOpenSuppliers = gtNetJpaRepository.findPushOpenSuppliers();
    List<GTNet> openSuppliers = gtNetJpaRepository.findOpenSuppliers();

    // Use Set to avoid duplicates (same peer could theoretically appear in both)
    Set<Integer> seenIds = new HashSet<>();
    List<GTNet> allSuppliers = new ArrayList<>();

    for (GTNet supplier : pushOpenSuppliers) {
      if (seenIds.add(supplier.getIdGtNet())) {
        allSuppliers.add(supplier);
      }
    }

    for (GTNet supplier : openSuppliers) {
      if (seenIds.add(supplier.getIdGtNet())) {
        allSuppliers.add(supplier);
      }
    }

    return allSuppliers;
  }

  @Override
  public boolean removeAllOtherPendingJobsOfSameTask() {
    return true;
  }
}
