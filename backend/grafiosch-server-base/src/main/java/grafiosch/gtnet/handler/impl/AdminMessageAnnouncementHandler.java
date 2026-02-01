package grafiosch.gtnet.handler.impl;

import java.util.Set;

import org.springframework.stereotype.Component;

import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.GTNetMessageCode;
import grafiosch.gtnet.handler.AbstractAnnouncementHandler;
import grafiosch.gtnet.handler.GTNetMessageContext;

/**
 * Handler for admin-to-admin messages (GT_NET_ADMIN_MESSAGE_SEL_C and GT_NET_ADMIN_MESSAGE_ALL_C).
 *
 * Admin messages are informational communications between GTNet administrators. They are stored for viewing but do not
 * trigger any automatic side effects. The visibility of these messages is controlled by the {@code visibility} field on
 * the GTNetMessage entity.
 *
 * <ul>
 *   <li>GT_NET_ADMIN_MESSAGE_SEL_C (30) - Targeted message to a specific GTNet domain</li>
 *   <li>GT_NET_ADMIN_MESSAGE_ALL_C (31) - Broadcast message to all authorized GTNet domains</li>
 * </ul>
 */
@Component
public class AdminMessageAnnouncementHandler extends AbstractAnnouncementHandler {

  @Override
  public GTNetMessageCode getSupportedMessageCode() {
    return GNetCoreMessageCode.GT_NET_ADMIN_MESSAGE_SEL_C;
  }

  @Override
  public Set<? extends GTNetMessageCode> getSupportedMessageCodes() {
    return Set.of(
        GNetCoreMessageCode.GT_NET_ADMIN_MESSAGE_SEL_C,
        GNetCoreMessageCode.GT_NET_ADMIN_MESSAGE_ALL_C);
  }

  @Override
  protected void processAnnouncementSideEffects(GTNetMessageContext context, GTNetMessage storedMessage) {
    // Admin messages are purely informational - no side effects needed.
    // The message is stored by AbstractAnnouncementHandler.storeIncomingMessage() and can be viewed by admins.
  }
}
