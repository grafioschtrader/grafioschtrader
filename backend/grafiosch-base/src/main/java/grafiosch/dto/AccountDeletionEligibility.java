package grafiosch.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Result of the read-only pre-check that drives the "delete my own account" menu item. It lets the frontend warn the
 * user up front that they must delete their managed clients or revoke shared access before they can delete their own
 * account, instead of showing the deletion confirmation only to have the backend reject it afterwards. The backend
 * delete path enforces the same conditions as a hard guarantee for non-frontend callers.
 */
@Schema(description = "Whether the current user may delete their own account, or why deletion is currently blocked.")
public class AccountDeletionEligibility {

  /**
   * Outcome of evaluating the current user's account against the self-deletion preconditions.
   *
   * <ul>
   * <li>{@code DELETABLE} - no managed clients and no shared viewers; deletion is allowed.</li>
   * <li>{@code HAS_CLIENTS} - the user still holds at least one MANAGE grant (manages a client); those clients must be
   * deleted first.</li>
   * <li>{@code HAS_VIEWERS} - others can still read the user's home tenant (a read grant or a co-resident read-only
   * viewer login); all shared access must be revoked first.</li>
   * </ul>
   */
  public enum DeletionEligibility {
    DELETABLE, HAS_CLIENTS, HAS_VIEWERS
  }

  @Schema(description = "Eligibility status: DELETABLE, HAS_CLIENTS or HAS_VIEWERS")
  private DeletionEligibility status;

  public AccountDeletionEligibility() {
  }

  public AccountDeletionEligibility(DeletionEligibility status) {
    this.status = status;
  }

  public DeletionEligibility getStatus() {
    return status;
  }

  public void setStatus(DeletionEligibility status) {
    this.status = status;
  }
}
