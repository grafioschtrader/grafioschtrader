package grafiosch.gtnet.handler.impl;

import java.util.Set;

import org.springframework.stereotype.Component;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.GTNetMessageCode;
import grafiosch.gtnet.IExchangeKindType;
import grafiosch.gtnet.handler.AbstractDataRevokeHandler;
import grafiosch.gtnet.handler.GTNetMessageContext;

/**
 * Handler for GT_NET_DATA_REVOKE_SEL_C messages.
 *
 * Processes revocations for any combination of syncable data types via the entityKinds parameter.
 * If no entityKinds parameter is provided, all syncable entity kinds are revoked.
 */
@Component
public class DataRevokeHandler extends AbstractDataRevokeHandler {

  @Override
  public GTNetMessageCode getSupportedMessageCode() {
    return GNetCoreMessageCode.GT_NET_DATA_REVOKE_SEL_C;
  }

  @Override
  protected void processAnnouncementSideEffects(GTNetMessageContext context, GTNetMessage storedMessage) {
    GTNet remoteGTNet = context.getRemoteGTNet();
    if (remoteGTNet == null) {
      return;
    }

    Set<IExchangeKindType> revokedKinds = getRevokedEntityKinds(context);

    for (IExchangeKindType kind : revokedKinds) {
      updateEntityForRevoke(remoteGTNet, kind);
    }

    saveRemoteGTNet(remoteGTNet);
  }
}
