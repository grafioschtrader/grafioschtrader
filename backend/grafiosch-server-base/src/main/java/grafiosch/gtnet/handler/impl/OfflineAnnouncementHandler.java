package grafiosch.gtnet.handler.impl;

import org.springframework.stereotype.Component;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.GTNetMessageCode;
import grafiosch.gtnet.GTNetServerStateTypes;
import grafiosch.gtnet.handler.AbstractAnnouncementHandler;
import grafiosch.gtnet.handler.GTNetMessageContext;

/**
 * Handler for GT_NET_OFFLINE_ALL_C messages.
 *
 * Processes offline announcements from remote servers. The remote server has gone offline and it is unknown when it
 * will be back online. This may be a restart or a shutdown.
 *
 * Updates the remote GTNet server state to closed for all entity kinds.
 */
@Component
public class OfflineAnnouncementHandler extends AbstractAnnouncementHandler {

  @Override
  public GTNetMessageCode getSupportedMessageCode() {
    return GNetCoreMessageCode.GT_NET_OFFLINE_ALL_C;
  }

  @Override
  protected void processAnnouncementSideEffects(GTNetMessageContext context, GTNetMessage storedMessage) {
    GTNet remoteGTNet = context.getRemoteGTNet();
    if (remoteGTNet == null) {
      return;
    }

    // Mark all entity kinds as closed - server is offline
    remoteGTNet.getGtNetEntities().forEach(entity ->
        entity.setServerState(GTNetServerStateTypes.SS_CLOSED));
    saveRemoteGTNet(remoteGTNet);
  }
}
