package grafioschtrader.gtnet.handler.impl;

import org.springframework.stereotype.Component;

import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.GTNetServerStateTypes;
import grafioschtrader.gtnet.handler.AbstractAnnouncementHandler;
import grafioschtrader.gtnet.handler.GTNetMessageContext;

/**
 * Handler for GT_NET_LASTPRICE_REVOKE_SEL_C messages.
 *
 * Processes revocation of lastprice data exchange from a remote server.
 */
@Component
public class LastpriceRevokeHandler extends AbstractAnnouncementHandler {

  @Override
  public GTNetMessageCodeType getSupportedMessageCode() {
    return GTNetMessageCodeType.GT_NET_LASTPRICE_REVOKE_SEL_C;
  }

  @Override
  protected void processAnnouncementSideEffects(GTNetMessageContext context, GTNetMessage storedMessage) {
    GTNet remoteGTNet = context.getRemoteGTNet();
    if (remoteGTNet == null) {
      return;
    }

    // Disable lastprice exchange with this remote
    remoteGTNet.setAcceptLastpriceRequest(false);
    remoteGTNet.setLastpriceServerState(GTNetServerStateTypes.SS_CLOSED);
    saveRemoteGTNet(remoteGTNet);
  }
}
