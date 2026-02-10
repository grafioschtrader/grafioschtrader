package grafioschtrader.task.exec;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import grafiosch.BaseConstants;
import grafiosch.entities.GTNet;
import grafiosch.entities.TaskDataChange;
import grafiosch.exceptions.TaskBackgroundException;
import grafiosch.repository.GTNetJpaRepository;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafiosch.types.TaskDataExecPriority;
import grafiosch.types.TaskTypeBase;
import grafioschtrader.service.GTNetExchangeSyncService;
import grafioschtrader.service.GlobalparametersService;

/**
 * Task that synchronizes GTNetExchange configurations with GTNet peers.
 *
 * This task supports two execution modes:
 * <ul>
 *   <li><b>Incremental mode</b> (idEntity = null): Only syncs changes since last sync timestamp.
 *       Used after data exchange acceptance.</li>
 *   <li><b>Full recreation mode</b> (idEntity = 1): Ignores timestamps and recreates all
 *       GTNetSupplierDetail entries for each peer. Used for daily scheduled sync and frontend-triggered sync.</li>
 * </ul>
 *
 * The task is triggered by:
 * <ul>
 *   <li>Daily cron schedule (configured via {@code gt.gtnet.exchange.sync.cron}) - full recreation mode</li>
 *   <li>Frontend trigger when user manually requests sync - full recreation mode</li>
 *   <li>After data exchange acceptance - incremental mode</li>
 * </ul>
 *
 * @see GTNetExchangeSyncService for the core sync logic
 */
@Component
public class GTNetExchangeSyncTask implements ITask {

  private static final Logger log = LoggerFactory.getLogger(GTNetExchangeSyncTask.class);

  /**
   * Entity type name for sync mode selection. Used in getAllowedEntities() and
   * GTNetExchangeSyncEntityIdOptionsProvider to allow admin to choose between sync modes.
   */
  public static final String SYNC_MODE_ENTITY = "SyncMode";

  /** Marker value for full recreation mode. When idEntity equals this value, full recreation is performed. */
  public static final Integer FULL_RECREATION_MODE = 1;

  /** Marker value for incremental mode. When idEntity equals this value, timestamp-based sync is performed. */
  public static final Integer INCREMENTAL_MODE = 0;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;
  
  @Autowired
  private GlobalparametersService globalparametersService;

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  private GTNetExchangeSyncService exchangeSyncService;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  @Override
  public ITaskType getTaskType() {
    return TaskTypeBase.GTNET_EXCHANGE_SYNC;
  }

  /**
   * Daily scheduled method that triggers full recreation sync.
   * Creates a TaskDataChange with FULL_RECREATION_MODE to be processed asynchronously.
   */
  @Scheduled(cron = "${gt.gtnet.exchange.sync.cron}", zone = BaseConstants.TIME_ZONE)
  public void scheduledGTNetExchangeSync() {
    taskDataChangeJpaRepository.save(new TaskDataChange(
        getTaskType(),
        TaskDataExecPriority.PRIO_NORMAL,
        LocalDateTime.now(),
        FULL_RECREATION_MODE,
        SYNC_MODE_ENTITY
    ));
    log.info("Scheduled GTNet exchange sync task created (full recreation mode)");
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException {
    if (!globalparametersJpaRepository.isGTNetEnabled()) {
      log.debug("GTNet is disabled, skipping exchange sync");
      return;
    }

    // Check if full recreation mode is requested
    boolean fullRecreation = FULL_RECREATION_MODE.equals(taskDataChange.getIdEntity());

    Date lastSyncTimestamp = globalparametersService.getGTNetExchangeSyncTimestamp();
    log.info("Starting GTNet exchange sync (fullRecreation={}, last sync: {})", fullRecreation, lastSyncTimestamp);

    // Get all accessible AC_OPEN suppliers, excluding own entry to prevent self-communication
    Integer myEntryId = globalparametersJpaRepository.getGTNetMyEntryID();
    List<GTNet> allSuppliers = gtNetJpaRepository.findAll().stream()
        .filter(peer -> myEntryId == null || !peer.getIdGtNet().equals(myEntryId))
        .collect(Collectors.toList());

    if (allSuppliers.isEmpty()) {
      log.info("No accessible peers configured for exchange sync");
      if (!fullRecreation) {
        globalparametersService.updateGTNetExchangeSyncTimestamp();
      }
      return;
    }

    int successCount = 0;
    int failCount = 0;

    for (GTNet peer : allSuppliers) {
      try {
        boolean success = exchangeSyncService.syncWithPeer(peer, lastSyncTimestamp, fullRecreation);
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

    // Update timestamp after job completion (only for incremental mode)
    if (!fullRecreation) {
      globalparametersService.updateGTNetExchangeSyncTimestamp();
    }
    log.info("GTNet exchange sync completed: {} successful, {} failed out of {} peers (fullRecreation={})",
        successCount, failCount, allSuppliers.size(), fullRecreation);
  }

  @Override
  public boolean removeAllOtherPendingJobsOfSameTask() {
    return true;
  }

  @Override
  public List<String> getAllowedEntities() {
    // SyncMode entity allows admin to choose between incremental and full recreation modes
    return Arrays.asList(SYNC_MODE_ENTITY);
  }
}
