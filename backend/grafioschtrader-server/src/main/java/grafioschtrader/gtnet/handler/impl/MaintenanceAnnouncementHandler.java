package grafioschtrader.gtnet.handler.impl;

import org.springframework.stereotype.Component;

import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.GTNetServerStateTypes;
import grafioschtrader.gtnet.handler.AbstractAnnouncementHandler;
import grafioschtrader.gtnet.handler.GTNetMessageContext;

/**
 * Handler for GT_NET_MAINTENANCE_ALL_C messages.
 *
 * Processes maintenance window announcements from remote servers. When received, updates the remote GTNet server state
 * to indicate maintenance mode.
 */
@Component
public class MaintenanceAnnouncementHandler extends AbstractAnnouncementHandler {

  @Override
  public GTNetMessageCodeType getSupportedMessageCode() {
    return GTNetMessageCodeType.GT_NET_MAINTENANCE_ALL_C;
  }

  @Override
  protected void processAnnouncementSideEffects(GTNetMessageContext context, GTNetMessage storedMessage) {
    GTNet remoteGTNet = context.getRemoteGTNet();
    if (remoteGTNet == null) {
      return;
    }

    // Update entity kinds to maintenance mode if they were previously accepted
    remoteGTNet.getGtNetEntities().stream()
        .filter(entity -> entity.isAcceptRequest())
        .forEach(entity -> entity.setServerState(GTNetServerStateTypes.SS_MAINTENANCE));
    saveRemoteGTNet(remoteGTNet);
  }
}
