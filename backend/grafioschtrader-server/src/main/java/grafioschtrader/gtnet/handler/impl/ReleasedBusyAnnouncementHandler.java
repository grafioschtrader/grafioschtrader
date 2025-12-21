package grafioschtrader.gtnet.handler.impl;

import org.springframework.stereotype.Component;

import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.GTNetServerStateTypes;
import grafioschtrader.gtnet.handler.AbstractAnnouncementHandler;
import grafioschtrader.gtnet.handler.GTNetMessageContext;

/**
 * Handler for GT_NET_RELEASED_BUSY_ALL_C messages.
 *
 * Processes released busy announcements from remote servers. The remote server is no longer at full capacity and can be
 * contacted again for data requests.
 *
 * Restores the remote GTNet server state to open for services that were previously accepted.
 */
@Component
public class ReleasedBusyAnnouncementHandler extends AbstractAnnouncementHandler {

  @Override
  public GTNetMessageCodeType getSupportedMessageCode() {
    return GTNetMessageCodeType.GT_NET_RELEASED_BUSY_ALL_C;
  }

  @Override
  protected void processAnnouncementSideEffects(GTNetMessageContext context, GTNetMessage storedMessage) {
    GTNet remoteGTNet = context.getRemoteGTNet();
    if (remoteGTNet == null) {
      return;
    }

    // Restore entity kinds to open state if they were previously accepted
    remoteGTNet.getGtNetEntities().stream()
        .filter(entity -> entity.isAcceptRequest())
        .forEach(entity -> entity.setServerState(GTNetServerStateTypes.SS_OPEN));
    saveRemoteGTNet(remoteGTNet);
  }
}
