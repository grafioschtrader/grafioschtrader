package grafioschtrader.gtnet.handler.impl;

import org.springframework.stereotype.Component;

import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.GTNetServerStateTypes;
import grafioschtrader.gtnet.handler.AbstractAnnouncementHandler;
import grafioschtrader.gtnet.handler.GTNetMessageContext;

/**
 * Handler for GT_NET_OPERATION_DISCONTINUED_ALL_C messages.
 *
 * Processes service discontinuation announcements from remote servers. When received, marks the remote GTNet as closed.
 */
@Component
public class OperationDiscontinuedHandler extends AbstractAnnouncementHandler {

  @Override
  public GTNetMessageCodeType getSupportedMessageCode() {
    return GTNetMessageCodeType.GT_NET_OPERATION_DISCONTINUED_ALL_C;
  }

  @Override
  protected void processAnnouncementSideEffects(GTNetMessageContext context, GTNetMessage storedMessage) {
    GTNet remoteGTNet = context.getRemoteGTNet();
    if (remoteGTNet == null) {
      return;
    }

    // Mark both services as closed
    remoteGTNet.setEntityServerState(GTNetServerStateTypes.SS_CLOSED);
    remoteGTNet.setLastpriceServerState(GTNetServerStateTypes.SS_CLOSED);
    remoteGTNet.setAcceptEntityRequest(false);
    remoteGTNet.setAcceptLastpriceRequest(false);
    saveRemoteGTNet(remoteGTNet);
  }
}
