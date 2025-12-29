package grafioschtrader.task.exec;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafiosch.entities.TaskDataChange;
import grafiosch.exceptions.TaskBackgroundException;
import grafiosch.task.ITask;
import grafiosch.types.ITaskType;
import grafioschtrader.entities.GTNet;
import grafioschtrader.gtnet.GTNetServerOnlineStatusTypes;
import grafioschtrader.m2m.GTNetMessageHelper;
import grafioschtrader.m2m.client.BaseDataClient;
import grafioschtrader.m2m.client.BaseDataClient.SendResult;
import grafioschtrader.repository.GTNetJpaRepository;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.TaskTypeExtended;

/**
 * Background task that checks and updates the online/busy status of all configured GTNet servers.
 *
 * This task is scheduled to run shortly after application startup (with a 30-second delay) to allow the server to
 * become fully accessible before checking peer status. For each configured GTNet entry (excluding the local server),
 * it sends a ping to determine reachability and updates the serverOnline and serverBusy flags accordingly.
 *
 * The 30-second delay ensures that:
 * <ul>
 *   <li>The server is fully initialized and accepting HTTP requests</li>
 *   <li>Network interfaces are ready</li>
 *   <li>Users can access the UI immediately while status checks happen in the background</li>
 * </ul>
 *
 */
@Component
public class GTNetServerStatusCheckTask implements ITask {

  private static final Logger log = LoggerFactory.getLogger(GTNetServerStatusCheckTask.class);

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  private GlobalparametersService globalparametersService;

  @Autowired
  private BaseDataClient baseDataClient;

  @Override
  public ITaskType getTaskType() {
    return TaskTypeExtended.GTNET_SERVER_STATUS_CHECK;
  }

  @Override
  public void doWork(TaskDataChange taskDataChange) throws TaskBackgroundException {
    if (!globalparametersService.isGTNetEnabled()) {
      log.debug("GTNet is disabled, skipping server status check");
      return;
    }

    Integer myEntryId = globalparametersService.getGTNetMyEntryID();
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
        log.debug("Skipping peer {} - no handshake completed", peer.getDomainRemoteName());
        continue;
      }
      checkedCount++;
      if (checkAndUpdatePeerStatus(peer, myGTNet)) {
        onlineCount++;
      }
    }

    log.info("GTNet server status check completed: {}/{} peers online", onlineCount, checkedCount);
  }

  /**
   * Sends a ping to the specified peer and updates its online/busy status based on the response.
   *
   * @param peer the GTNet peer to check
   * @param myGTNet the local GTNet entry used as the sender
   * @return true if the peer is online, false otherwise
   */
  private boolean checkAndUpdatePeerStatus(GTNet peer, GTNet myGTNet) {
    GTNetServerOnlineStatusTypes previousStatus = peer.getServerOnline();

    try {
      SendResult result = GTNetMessageHelper.sendPingWithStatus(baseDataClient, myGTNet, peer);

      GTNetServerOnlineStatusTypes newStatus = GTNetServerOnlineStatusTypes.fromReachable(result.serverReachable());
      peer.setServerOnline(newStatus);
      if (result.serverReachable() && result.response() != null) {
        peer.setServerBusy(result.serverBusy());
      }

      if (result.serverReachable()) {
        log.debug("Peer {} is online (busy={})", peer.getDomainRemoteName(), peer.isServerBusy());
      } else {
        log.debug("Peer {} is offline", peer.getDomainRemoteName());
      }

      if (previousStatus != newStatus) {
        gtNetJpaRepository.save(peer);
      }

      return result.serverReachable();
    } catch (Exception e) {
      log.warn("Error checking status for peer {}: {}", peer.getDomainRemoteName(), e.getMessage());
      if (previousStatus == GTNetServerOnlineStatusTypes.SOS_ONLINE) {
        peer.setServerOnline(GTNetServerOnlineStatusTypes.SOS_OFFLINE);
        gtNetJpaRepository.save(peer);
      }
      return false;
    }
  }

  @Override
  public boolean removeAllOtherPendingJobsOfSameTask() {
    // Remove any other pending status check tasks to avoid duplicate checks
    return true;
  }
}
