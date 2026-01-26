package grafiosch.gtnet.handler.impl;

import org.springframework.stereotype.Component;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.GTNetMessageCode;
import grafiosch.gtnet.handler.AbstractAnnouncementHandler;
import grafiosch.gtnet.handler.GTNetMessageContext;

/**
 * Handler for GT_NET_SETTINGS_UPDATED_ALL_C messages.
 *
 * Processes settings update announcements from remote servers. When a remote server updates their GTNet or GTNetEntity
 * settings (dailyRequestLimit, acceptRequest, serverState, maxLimit), they broadcast this announcement.
 *
 * The actual settings sync happens automatically via updateRemoteGTNetFromEnvelope() which processes the
 * MessageEnvelope.sourceGtNet before this handler runs. This handler exists primarily to acknowledge the announcement
 * and ensure the updated remote entry is persisted.
 */
@Component
public class SettingsUpdatedAnnouncementHandler extends AbstractAnnouncementHandler {

  @Override
  public GTNetMessageCode getSupportedMessageCode() {
    return GNetCoreMessageCode.GT_NET_SETTINGS_UPDATED_ALL_C;
  }

  @Override
  protected void processAnnouncementSideEffects(GTNetMessageContext context, GTNetMessage storedMessage) {
    GTNet remoteGTNet = context.getRemoteGTNet();
    if (remoteGTNet == null) {
      return;
    }

    // Settings are already synced via updateRemoteGTNetFromEnvelope() which runs before handlers.
    // Just save the updated remote entry to ensure all changes are persisted.
    saveRemoteGTNet(remoteGTNet);
  }
}
