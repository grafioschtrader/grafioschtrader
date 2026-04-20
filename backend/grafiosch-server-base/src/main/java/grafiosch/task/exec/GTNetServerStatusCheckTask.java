package grafiosch.task.exec;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafiosch.entities.GTNet;
import grafiosch.entities.TaskDataChange;
import grafiosch.exceptions.TaskBackgroundException;
import grafiosch.gtnet.GTNetServerOnlineStatusTypes;
import grafiosch.gtnet.GTNetStatusCheckService;
import grafiosch.repository.GTNetJpaRepository;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafiosch.types.TaskTypeBase;

/**
 * Background task that refreshes the online status of every configured GTNet peer.
 *
 * <p>Orchestration only — the actual probe and DB update logic lives in
 * {@link GTNetStatusCheckService} so that an administrator can trigger the same check on a
 * single peer from the UI without duplicating code.
 *
 * <p>Scheduling: one run is queued 30 seconds after application startup
 * (see {@code GTNetLifecycleListener}). The task does not schedule itself recurrently.
 */
@Component
public class GTNetServerStatusCheckTask implements ITask {

  private static final Logger log = LoggerFactory.getLogger(GTNetServerStatusCheckTask.class);

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  private GTNetStatusCheckService statusCheckService;

  @Override
  public ITaskType getTaskType() {
    return TaskTypeBase.GTNET_SERVER_STATUS_CHECK;
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException {
    if (!globalparametersJpaRepository.isGTNetEnabled()) {
      log.debug("GTNet is disabled, skipping server status check");
      return;
    }

    Integer myEntryId = globalparametersJpaRepository.getGTNetMyEntryID();
    if (myEntryId == null) {
      log.info("GTNet my entry ID not configured, skipping server status check");
      return;
    }

    GTNet myGTNet = gtNetJpaRepository.findById(myEntryId).orElse(null);
    if (myGTNet == null) {
      log.warn("GTNet my entry not found with ID: {}", myEntryId);
      return;
    }

    List<GTNet> allPeers = gtNetJpaRepository.findWithConfiguredExchange();
    log.info("Starting GTNet server status check for {} peers", allPeers.size());

    int checkedCount = 0;
    int onlineCount = 0;

    for (GTNet peer : allPeers) {
      if (peer.getIdGtNet().equals(myEntryId)) {
        continue;
      }
      if (peer.getGtNetConfig() == null || peer.getGtNetConfig().getTokenRemote() == null) {
        // Outbound handshake incomplete — we cannot ping. Reset any stale ONLINE flag set
        // by an inbound handshake envelope so the UI does not show a false positive.
        statusCheckService.markUnverifiable(peer);
        continue;
      }
      checkedCount++;
      GTNet updated = statusCheckService.checkAndUpdatePeer(peer, myGTNet);
      if (updated.getServerOnline() == GTNetServerOnlineStatusTypes.SOS_ONLINE) {
        onlineCount++;
      }
    }

    log.info("GTNet server status check completed: {}/{} peers online", onlineCount, checkedCount);
  }

  @Override
  public boolean removeAllOtherPendingJobsOfSameTask() {
    return true;
  }
}
