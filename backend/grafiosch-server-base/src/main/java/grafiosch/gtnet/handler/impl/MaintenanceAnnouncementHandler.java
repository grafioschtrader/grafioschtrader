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
 * Handler for GT_NET_MAINTENANCE_ALL_C messages.
 *
 * Processes maintenance window announcements from remote servers. When received, updates the remote GTNet server state
 * to indicate maintenance mode.
 */
@Component
public class MaintenanceAnnouncementHandler extends AbstractAnnouncementHandler {

  @Override
  public GTNetMessageCode getSupportedMessageCode() {
    return GNetCoreMessageCode.GT_NET_MAINTENANCE_ALL_C;
  }

  @Override
  protected void processAnnouncementSideEffects(GTNetMessageContext context, GTNetMessage storedMessage) {
    GTNet remoteGTNet = context.getRemoteGTNet();
    if (remoteGTNet == null) {
      return;
    }

    // Update entity kinds to maintenance mode if they were previously accepting requests
    remoteGTNet.getGtNetEntities().stream()
        .filter(entity -> entity.isAccepting())
        .forEach(entity -> entity.setServerState(GTNetServerStateTypes.SS_MAINTENANCE));
    saveRemoteGTNet(remoteGTNet);
  }
}
