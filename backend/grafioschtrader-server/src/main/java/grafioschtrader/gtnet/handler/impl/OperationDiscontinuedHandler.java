package grafioschtrader.gtnet.handler.impl;

import org.springframework.stereotype.Component;

import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.gtnet.AcceptRequestTypes;
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

    // Mark all entity kinds as closed and not accepting requests
    remoteGTNet.getGtNetEntities().forEach(entity -> {
      entity.setServerState(GTNetServerStateTypes.SS_CLOSED);
      entity.setAcceptRequest(AcceptRequestTypes.AC_CLOSED);
    });
    saveRemoteGTNet(remoteGTNet);
  }
}
