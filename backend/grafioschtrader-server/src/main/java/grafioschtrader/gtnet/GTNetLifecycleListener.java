package grafioschtrader.gtnet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import grafioschtrader.gtnet.model.MsgRequest;
import grafioschtrader.repository.GTNetJpaRepository;
import grafioschtrader.service.GlobalparametersService;

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

  @Value("${gt.use.gtnet:false}")
  private boolean gtnetEnabled;

  @Autowired
  @Lazy
  private GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  @Lazy
  private GlobalparametersService globalparametersService;

  /**
   * Publishes GT_NET_ONLINE_ALL_C when the application is fully started.
   *
   * Uses ApplicationReadyEvent to ensure all beans are initialized and the application is ready to serve requests
   * before announcing online status to peers.
   *
   * @param event the application ready event
   */
  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady(ApplicationReadyEvent event) {
    if (!gtnetEnabled) {
      log.debug("GTNet is disabled, skipping online announcement");
      return;
    }

    // Check if GTNet is configured (has my entry ID)
    Integer myEntryId = globalparametersService.getGTNetMyEntryID();
    if (myEntryId == null) {
      log.info("GTNet my entry ID not configured, skipping online announcement");
      return;
    }

    try {
      log.info("Publishing GT_NET_ONLINE_ALL_C to all peers");
      MsgRequest msgRequest = new MsgRequest();
      msgRequest.messageCode = GTNetMessageCodeType.GT_NET_ONLINE_ALL_C;
      gtNetJpaRepository.submitMsg(msgRequest);
      log.info("Successfully published online announcement to GTNet peers");
    } catch (Exception e) {
      log.error("Failed to publish online announcement to GTNet peers", e);
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
    if (!gtnetEnabled) {
      log.debug("GTNet is disabled, skipping offline announcement");
      return;
    }

    // Check if GTNet is configured (has my entry ID)
    Integer myEntryId = globalparametersService.getGTNetMyEntryID();
    if (myEntryId == null) {
      log.info("GTNet my entry ID not configured, skipping offline announcement");
      return;
    }

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
