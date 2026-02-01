package grafiosch.gtnet;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import grafiosch.entities.GTNet;
import grafiosch.entities.TaskDataChange;
import grafiosch.gtnet.model.MsgRequest;
import grafiosch.repository.GTNetJpaRepository;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.types.TaskDataExecPriority;
import grafiosch.types.TaskTypeBase;

/**
 * Lifecycle listener for GTNet that handles startup and shutdown tasks.
 *
 * On startup, schedules a background task to check peer status via ping. When peers respond to pings, both sides
 * automatically update each other's online status - no explicit online announcement is needed.
 *
 * On shutdown, broadcasts GT_NET_OFFLINE_ALL_C to inform peers the server is going offline (immediate notification
 * is needed since failed pings would take too long to detect).
 *
 * This listener only executes if GTNet is enabled via the {@code gt.use.gtnet} configuration property.
 *
 * @see GNetCoreMessageCode#GT_NET_OFFLINE_ALL_C
 */
@Component
public class GTNetLifecycleListener {

  private static final Logger log = LoggerFactory.getLogger(GTNetLifecycleListener.class);

  /** Delay in seconds before the GTNet server status check task runs after startup. */
  private static final int STARTUP_DELAY_SECONDS = 30;

  @Autowired
  @Lazy
  private GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  @Lazy
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  /**
   * Schedules the GTNet server status check task when the application is fully started.
   *
   * Instead of directly checking peer status (which would block startup), this method schedules a background task to
   * run after a 30-second delay. This ensures:
   * <ul>
   * <li>The server is fully accessible from outside before checking peers</li>
   * <li>Users can access the UI immediately without waiting for network operations</li>
   * <li>Unreachable peers don't delay application startup</li>
   * </ul>
   *
   * @param event the application ready event
   */
  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady(ApplicationReadyEvent event) {
    if (checkGTNetIsUsedAndSetup()) {
      try {
        // Ensure local server shows as online
        ensureLocalServerOnlineStatus();

        log.info("Scheduling GTNet server status check task to run in {} seconds", STARTUP_DELAY_SECONDS);
        TaskDataChange taskDataChange = new TaskDataChange(TaskTypeBase.GTNET_SERVER_STATUS_CHECK,
            TaskDataExecPriority.PRIO_NORMAL, LocalDateTime.now().plusSeconds(STARTUP_DELAY_SECONDS));
        taskDataChangeJpaRepository.save(taskDataChange);
        log.info("GTNet server status check task scheduled successfully");
      } catch (Exception e) {
        log.error("Failed to schedule GTNet server status check task", e);
      }
    }
  }

  /**
   * Ensures the local GTNet server entry has SOS_ONLINE status.
   * The local server is always online when the application is running.
   */
  private void ensureLocalServerOnlineStatus() {
    Integer myEntryId = globalparametersJpaRepository.getGTNetMyEntryID();
    if (myEntryId != null) {
      GTNet myGTNet = gtNetJpaRepository.findById(myEntryId).orElse(null);
      if (myGTNet != null && myGTNet.getServerOnline() != GTNetServerOnlineStatusTypes.SOS_ONLINE) {
        myGTNet.setServerOnline(GTNetServerOnlineStatusTypes.SOS_ONLINE);
        gtNetJpaRepository.save(myGTNet);
        log.info("Set local GTNet server status to ONLINE");
      }
    }
  }

  /**
   * Publishes GT_NET_OFFLINE_ALL_C when the application is shutting down.
   *
   * Uses ContextClosedEvent to send the offline announcement before Spring context destruction begins. This gives peers
   * advance notice that the server is going offline.
   *
   * @param event the context closed event
   */
  @EventListener(ContextClosedEvent.class)
  public void onContextClosed(ContextClosedEvent event) {
    if (checkGTNetIsUsedAndSetup()) {
      try {
        log.info("Publishing GT_NET_OFFLINE_ALL_C to all peers");
        MsgRequest msgRequest = new MsgRequest();
        msgRequest.messageCode = GNetCoreMessageCode.GT_NET_OFFLINE_ALL_C.name();
        gtNetJpaRepository.submitMsg(msgRequest);
        log.info("Successfully published offline announcement to GTNet peers");
      } catch (Exception e) {
        log.error("Failed to publish offline announcement to GTNet peers", e);
      }
    }
  }

  private boolean checkGTNetIsUsedAndSetup() {
    if (!globalparametersJpaRepository.isGTNetEnabled()) {
      log.debug("GTNet is disabled, skipping offline announcement");
      return false;
    }
    // Check if GTNet is configured (has my entry ID)
    Integer myEntryId = globalparametersJpaRepository.getGTNetMyEntryID();
    if (myEntryId == null) {
      log.info("GTNet my entry ID not configured, skipping offline announcement");
      return false;
    }
    return true;
  }
}
