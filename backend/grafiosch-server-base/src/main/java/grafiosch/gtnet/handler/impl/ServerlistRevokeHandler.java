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

    // Only our own grant is cleared. Writing spreadCapability here was the mirror image of what the sender meant: that
    // flag is the peer's own published property - whether its entry may be redistributed - and it is re-synchronised
    // from the peer's DTO on its very next message, so the write was undone anyway.
    GTNetConfig config = remoteGTNet.getGtNetConfig();
    if (config != null && config.isServerlistAccessGranted()) {
      config.setServerlistAccessGranted(false);
      saveGTNetConfig(config);
      log.info("Revoked server list access for {} due to their revoke message", context.getSourceDomain());
    }
  }
}
