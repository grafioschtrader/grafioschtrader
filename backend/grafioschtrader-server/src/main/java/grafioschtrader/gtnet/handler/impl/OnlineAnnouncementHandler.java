package grafioschtrader.gtnet.handler.impl;

import org.springframework.stereotype.Component;

import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.GTNetServerStateTypes;
import grafioschtrader.gtnet.handler.AbstractAnnouncementHandler;
import grafioschtrader.gtnet.handler.GTNetMessageContext;

/**
 * Handler for GT_NET_ONLINE_ALL_C messages.
 *
 * Processes online announcements from remote servers. The remote server has come back online after being offline or
 * after a restart.
 *
 * Restores the remote GTNet server state to open for entity kinds that were previously accepted.
 */
@Component
public class OnlineAnnouncementHandler extends AbstractAnnouncementHandler {

  @Override
  public GTNetMessageCodeType getSupportedMessageCode() {
    return GTNetMessageCodeType.GT_NET_ONLINE_ALL_C;
  }

  @Override
  protected void processAnnouncementSideEffects(GTNetMessageContext context, GTNetMessage storedMessage) {
    GTNet remoteGTNet = context.getRemoteGTNet();
    if (remoteGTNet == null) {
      return;
    }

    // Restore entity kinds to open state if they were previously accepting requests
    remoteGTNet.getGtNetEntities().stream()
        .filter(entity -> entity.isAccepting())
        .forEach(entity -> entity.setServerState(GTNetServerStateTypes.SS_OPEN));
    saveRemoteGTNet(remoteGTNet);
  }
}
