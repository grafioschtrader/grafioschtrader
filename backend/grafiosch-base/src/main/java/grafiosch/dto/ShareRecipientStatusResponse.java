package grafiosch.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response of the recipient lookup that drives the share-read-access dialog. Lets the frontend decide whether to ask for
 * a password (only a brand-new recipient needs one) and whether to reject the e-mail up front.
 */
@Schema(description = "Status of an e-mail the owner wants to share read access with, used to drive the share dialog.")
public class ShareRecipientStatusResponse {

  /**
   * Outcome of looking up a share recipient e-mail against the owner's tenant.
   *
   * <ul>
   * <li>{@code SELF} - the e-mail is the owner's own login (must be rejected).</li>
   * <li>{@code ALREADY_SHARED} - the e-mail already has read access (grant or viewer login; must be rejected).</li>
   * <li>{@code EXISTS} - a registered user with no access yet; sharing creates a read grant, no password needed.</li>
   * <li>{@code NEW} - not registered; sharing creates a read-only viewer login and needs an initial password.</li>
   * </ul>
   */
  public enum ShareRecipientStatus {
    SELF, ALREADY_SHARED, EXISTS, NEW
  }

  @Schema(description = "Recipient status: SELF, ALREADY_SHARED, EXISTS (registered) or NEW (not yet registered)")
  private ShareRecipientStatus status;

  public ShareRecipientStatusResponse() {
  }

  public ShareRecipientStatusResponse(ShareRecipientStatus status) {
    this.status = status;
  }

  public ShareRecipientStatus getStatus() {
    return status;
  }

  public void setStatus(ShareRecipientStatus status) {
    this.status = status;
  }
}
