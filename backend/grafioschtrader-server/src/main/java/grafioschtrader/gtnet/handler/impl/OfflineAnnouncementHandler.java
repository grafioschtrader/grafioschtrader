package grafioschtrader.gtnet.handler.impl;

import org.springframework.stereotype.Component;

import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.GTNetServerStateTypes;
import grafioschtrader.gtnet.handler.AbstractAnnouncementHandler;
import grafioschtrader.gtnet.handler.GTNetMessageContext;

/**
 * Handler for GT_NET_OFFLINE_ALL_C messages.
 *
 * Processes offline announcements from remote servers. The remote server has gone offline and it is unknown when it
 * will be back online. This may be a restart or a shutdown.
 *
 * Updates the remote GTNet server state to closed for both entity and lastprice services.
 */
@Component
public class OfflineAnnouncementHandler extends AbstractAnnouncementHandler {

  @Override
  public GTNetMessageCodeType getSupportedMessageCode() {
    return GTNetMessageCodeType.GT_NET_OFFLINE_ALL_C;
  }

  @Override
  protected void processAnnouncementSideEffects(GTNetMessageContext context, GTNetMessage storedMessage) {
    GTNet remoteGTNet = context.getRemoteGTNet();
    if (remoteGTNet == null) {
      return;
    }

    // Mark both services as closed - server is offline
    remoteGTNet.setEntityServerState(GTNetServerStateTypes.SS_CLOSED);
    remoteGTNet.setLastpriceServerState(GTNetServerStateTypes.SS_CLOSED);
    saveRemoteGTNet(remoteGTNet);
  }
}
