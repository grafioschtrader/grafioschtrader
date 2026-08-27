package grafiosch.gtnet.handler.impl;

import org.springframework.stereotype.Component;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.GTNetMessageCode;
import grafiosch.gtnet.GTNetServerOnlineStatusTypes;
import grafiosch.gtnet.GTNetServerStateTypes;
import grafiosch.gtnet.handler.AbstractAnnouncementHandler;
import grafiosch.gtnet.handler.GTNetMessageContext;

/**
 * Handler for GT_NET_OFFLINE_ALL_C messages.
 *
 * Processes offline announcements from remote servers. The remote server has gone offline and it is unknown when it
 * will be back online. This may be a restart or a shutdown.
 *
 * Records the remote as offline and closes every entity kind, so a graceful shutdown is visible before the next
 * outbound send fails.
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

    // Both halves are needed. Closing the entity kinds alone left the peer recorded as online with everything closed,
    // because the status synchronization that runs ahead of every handler had already forced SOS_ONLINE - the peer had
    // after all just communicated. That synchronization now skips this code, so the announcement is what decides.
    remoteGTNet.setServerOnline(GTNetServerOnlineStatusTypes.SOS_OFFLINE);
    remoteGTNet.getGtNetEntities().forEach(entity -> entity.setServerState(GTNetServerStateTypes.SS_CLOSED));
    saveRemoteGTNet(remoteGTNet);
  }
}
