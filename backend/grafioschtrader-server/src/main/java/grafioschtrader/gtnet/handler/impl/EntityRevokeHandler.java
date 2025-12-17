package grafioschtrader.gtnet.handler.impl;

import org.springframework.stereotype.Component;

import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.GTNetServerStateTypes;
import grafioschtrader.gtnet.handler.AbstractAnnouncementHandler;
import grafioschtrader.gtnet.handler.GTNetMessageContext;

/**
 * Handler for GT_NET_ENTITY_REVOKE_SEL_C messages.
 *
 * Processes revocation of entity data exchange from a remote server. The remote is indicating they no longer wish to
 * share entity data.
 */
@Component
public class EntityRevokeHandler extends AbstractAnnouncementHandler {

  @Override
  public GTNetMessageCodeType getSupportedMessageCode() {
    return GTNetMessageCodeType.GT_NET_ENTITY_REVOKE_SEL_C;
  }

  @Override
  protected void processAnnouncementSideEffects(GTNetMessageContext context, GTNetMessage storedMessage) {
    GTNet remoteGTNet = context.getRemoteGTNet();
    if (remoteGTNet == null) {
      return;
    }

    // Disable entity exchange with this remote
    remoteGTNet.setAcceptEntityRequest(false);
    remoteGTNet.setEntityServerState(GTNetServerStateTypes.SS_CLOSED);
    saveRemoteGTNet(remoteGTNet);
  }
}
