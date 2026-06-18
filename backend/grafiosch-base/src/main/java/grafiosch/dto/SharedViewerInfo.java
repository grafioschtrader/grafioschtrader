package grafiosch.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO describing one person who can read the current owner's portfolio, returned by GET /tenant/shares and used
 * to populate the "shared viewers" management table in the frontend.
 *
 * <p>
 * Two kinds are distinguished by {@link #viewerType}: a {@code GRANT} is a registered user holding a read-only
 * {@code tenant_access} grant to the owner's tenant (revoking only removes the grant, the user keeps their own account),
 * while a {@code VIEWER} is a pure read-only viewer login whose home tenant is the owner's tenant (revoking deletes that
 * viewer login entirely).
 * </p>
 */
@Schema(description = "A person who can read the current owner's portfolio, with the kind of access (GRANT or VIEWER).")
public class SharedViewerInfo {

  /** Distinguishes a registered grantee (GRANT) from a pure read-only viewer login (VIEWER). */
  public enum SharedViewerType {
    GRANT, VIEWER
  }

  @Schema(description = "ID of the user that can read the owner's portfolio")
  private Integer idUser;

  @Schema(description = "Login e-mail of the user that can read the owner's portfolio")
  private String email;

  @Schema(description = "Kind of access: GRANT (registered user with a read grant) or VIEWER (pure read-only login)")
  private SharedViewerType viewerType;

  public SharedViewerInfo() {
  }

  public SharedViewerInfo(Integer idUser, String email, SharedViewerType viewerType) {
    this.idUser = idUser;
    this.email = email;
    this.viewerType = viewerType;
  }

  public Integer getIdUser() {
    return idUser;
  }

  public void setIdUser(Integer idUser) {
    this.idUser = idUser;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public SharedViewerType getViewerType() {
    return viewerType;
  }

  public void setViewerType(SharedViewerType viewerType) {
    this.viewerType = viewerType;
  }
}
