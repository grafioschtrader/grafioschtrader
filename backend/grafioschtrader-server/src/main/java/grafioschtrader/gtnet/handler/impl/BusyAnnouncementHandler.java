package grafioschtrader.gtnet.handler.impl;

import org.springframework.stereotype.Component;

import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.GTNetServerStateTypes;
import grafioschtrader.gtnet.handler.AbstractAnnouncementHandler;
import grafioschtrader.gtnet.handler.GTNetMessageContext;

/**
 * Handler for GT_NET_BUSY_ALL_C messages.
 *
 * Processes busy announcements from remote servers. The remote server is at full capacity and should no longer be
 * contacted for data requests. Only status change messages should be sent to this server.
 *
 * Sets the remote GTNet server state to closed to prevent further data requests.
 */
@Component
public class BusyAnnouncementHandler extends AbstractAnnouncementHandler {

  @Override
  public GTNetMessageCodeType getSupportedMessageCode() {
    return GTNetMessageCodeType.GT_NET_BUSY_ALL_C;
  }

  @Override
  protected void processAnnouncementSideEffects(GTNetMessageContext context, GTNetMessage storedMessage) {
    GTNet remoteGTNet = context.getRemoteGTNet();
    if (remoteGTNet == null) {
      return;
    }

    // Mark all entity kinds as closed - server is busy, do not contact for data
    remoteGTNet.getGtNetEntities().forEach(entity ->
        entity.setServerState(GTNetServerStateTypes.SS_CLOSED));
    saveRemoteGTNet(remoteGTNet);
  }
}
