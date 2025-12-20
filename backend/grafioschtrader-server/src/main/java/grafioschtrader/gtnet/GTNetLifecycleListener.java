package grafioschtrader.gtnet;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import grafiosch.entities.TaskDataChange;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.gtnet.model.MsgRequest;
import grafioschtrader.repository.GTNetJpaRepository;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.TaskTypeExtended;

/**
 * Lifecycle listener for GTNet that publishes online/offline announcements.
 *
 * When the server starts up, broadcasts GT_NET_ONLINE_ALL_C to all connected peers. When the server shuts down,
 * broadcasts GT_NET_OFFLINE_ALL_C to inform peers the server is going offline.
 *
 * This listener only executes if GTNet is enabled via the {@code gt.use.gtnet} configuration property.
 *
 * @see GTNetMessageCodeType#GT_NET_ONLINE_ALL_C
 * @see GTNetMessageCodeType#GT_NET_OFFLINE_ALL_C
 */
@Component
public class GTNetLifecycleListener {

  private static final Logger log = LoggerFactory.getLogger(GTNetLifecycleListener.class);

  /** Delay in seconds before the GTNet server status check task runs after startup. */
  private static final int STARTUP_DELAY_SECONDS = 30;

  @Value("${gt.use.gtnet:false}")
  private boolean gtnetEnabled;

  @Autowired
  @Lazy
  private GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  @Lazy
  private GlobalparametersService globalparametersService;

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
        log.info("Scheduling GTNet server status check task to run in {} seconds", STARTUP_DELAY_SECONDS);
        TaskDataChange taskDataChange = new TaskDataChange(TaskTypeExtended.GTNET_SERVER_STATUS_CHECK,
            TaskDataExecPriority.PRIO_NORMAL, LocalDateTime.now().plusSeconds(STARTUP_DELAY_SECONDS));
        taskDataChangeJpaRepository.save(taskDataChange);
        log.info("GTNet server status check task scheduled successfully");
      } catch (Exception e) {
        log.error("Failed to schedule GTNet server status check task", e);
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
        msgRequest.messageCode = GTNetMessageCodeType.GT_NET_OFFLINE_ALL_C;
        gtNetJpaRepository.submitMsg(msgRequest);
        log.info("Successfully published offline announcement to GTNet peers");
      } catch (Exception e) {
        log.error("Failed to publish offline announcement to GTNet peers", e);
      }
    }
  }

  private boolean checkGTNetIsUsedAndSetup() {
    if (!gtnetEnabled) {
      log.debug("GTNet is disabled, skipping offline announcement");
      return false;
    }
    // Check if GTNet is configured (has my entry ID)
    Integer myEntryId = globalparametersService.getGTNetMyEntryID();
    if (myEntryId == null) {
      log.info("GTNet my entry ID not configured, skipping offline announcement");
      return false;
    }
    return true;
  }
}
