package grafioschtrader.gtnet.handler;

import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.gtnet.MessageCategory;

/**
 * Abstract base class for handlers that process announcement messages.
 *
 * Announcement messages are one-way notifications that do not expect a response. Examples include:
 * <ul>
 *   <li>Maintenance window announcements</li>
 *   <li>Service discontinuation notices</li>
 *   <li>Revoke messages (canceling previous agreements)</li>
 * </ul>
 *
 * The handler stores the announcement for admin visibility and optionally updates local state.
 */
public abstract class AbstractAnnouncementHandler extends AbstractGTNetMessageHandler {

  @Override
  public final MessageCategory getCategory() {
    return MessageCategory.ANNOUNCEMENT;
  }

  @Override
  public final HandlerResult handle(GTNetMessageContext context) throws Exception {
    // 1. Validate if needed
    ValidationResult validation = validateAnnouncement(context);
    if (!validation.valid()) {
      return new HandlerResult.ProcessingError(validation.errorCode(), validation.message());
    }

    // 2. Store the announcement message
    GTNetMessage storedMessage = storeIncomingMessage(context);

    // 3. Process announcement-specific side effects (e.g., update server state)
    processAnnouncementSideEffects(context, storedMessage);

    // 4. No response for announcements
    return new HandlerResult.NoResponseNeeded();
  }

  /**
   * Validates the announcement message.
   *
   * Default implementation always returns valid. Override to add validation if needed.
   *
   * @param context the message context
   * @return validation result
   */
  protected ValidationResult validateAnnouncement(GTNetMessageContext context) {
    return ValidationResult.ok();
  }

  /**
   * Processes announcement-specific side effects.
   *
   * Called after the announcement is stored. Use for operations like:
   * <ul>
   *   <li>Updating GTNet server state (e.g., mark as maintenance)</li>
   *   <li>Scheduling local tasks based on announcement content</li>
   *   <li>Revoking previously granted permissions</li>
   * </ul>
   *
   * @param context       the message context
   * @param storedMessage the persisted announcement message
   */
  protected abstract void processAnnouncementSideEffects(GTNetMessageContext context, GTNetMessage storedMessage);
}
