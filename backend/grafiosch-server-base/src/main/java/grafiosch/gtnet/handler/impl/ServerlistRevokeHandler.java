package grafiosch.gtnet.handler.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetConfig;
import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.GTNetMessageCode;
import grafiosch.gtnet.handler.AbstractAnnouncementHandler;
import grafiosch.gtnet.handler.GTNetMessageContext;

/**
 * Handler for GT_NET_UPDATE_SERVERLIST_REVOKE_SEL_C messages.
 *
 * Processes revocation of server list sharing from a remote server. When a remote sends this message, it means they no
 * longer want to share their server list with us, and we should also revoke any access we granted them.
 */
@Component
public class ServerlistRevokeHandler extends AbstractAnnouncementHandler {

  private static final Logger log = LoggerFactory.getLogger(ServerlistRevokeHandler.class);

  @Override
  public GTNetMessageCode getSupportedMessageCode() {
    return GNetCoreMessageCode.GT_NET_UPDATE_SERVERLIST_REVOKE_SEL_C;
  }

  @Override
  protected void processAnnouncementSideEffects(GTNetMessageContext context, GTNetMessage storedMessage) {
    GTNet remoteGTNet = context.getRemoteGTNet();
    if (remoteGTNet == null) {
      return;
    }

    // Disable spread capability for this remote (they no longer share with us)
    remoteGTNet.setSpreadCapability(false);

    // Also revoke their access to our server list
    GTNetConfig config = remoteGTNet.getGtNetConfig();
    if (config != null && config.isServerlistAccessGranted()) {
      config.setServerlistAccessGranted(false);
      saveGTNetConfig(config);
      log.info("Revoked server list access for {} due to their revoke message", context.getSourceDomain());
    }

    saveRemoteGTNet(remoteGTNet);
  }
}
