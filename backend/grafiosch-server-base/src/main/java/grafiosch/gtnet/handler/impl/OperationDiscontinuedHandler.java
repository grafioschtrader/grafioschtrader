package grafiosch.gtnet.handler.impl;

import org.springframework.stereotype.Component;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.AcceptRequestTypes;
import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.GTNetMessageCode;
import grafiosch.gtnet.GTNetServerStateTypes;
import grafiosch.gtnet.handler.AbstractAnnouncementHandler;
import grafiosch.gtnet.handler.GTNetMessageContext;

/**
 * Handler for GT_NET_OPERATION_DISCONTINUED_ALL_C messages.
 *
 * Processes service discontinuation announcements from remote servers. When received, marks the remote GTNet as closed.
 */
@Component
public class OperationDiscontinuedHandler extends AbstractAnnouncementHandler {

  @Override
  public GTNetMessageCode getSupportedMessageCode() {
    return GNetCoreMessageCode.GT_NET_OPERATION_DISCONTINUED_ALL_C;
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
