package grafiosch.dto;

import grafiosch.types.TenantAccessLevel;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO describing one tenant the current user may access, used to populate the client/tenant switcher in the
 * frontend. Includes the user's home tenant as well as every tenant granted through a {@code tenant_access} row.
 */
@Schema(description = "A tenant the current user may access, with the user's access level and whether it is read-only.")
public class TenantAccessInfo {

  @Schema(description = "ID of the accessible tenant")
  private Integer idTenant;

  @Schema(description = "Display name of the tenant")
  private String tenantName;

  @Schema(description = "Login e-mail of the user owning this tenant (the read-only client for a granted tenant)")
  private String email;

  @Schema(description = "True if this is the user's own home tenant (as opposed to a granted tenant)")
  private boolean home;

  @Schema(description = "Access level the user holds on this tenant: READ or MANAGE")
  private TenantAccessLevel accessLevel;

  @Schema(description = "Convenience flag: true when the user has only read (view-only) access to this tenant")
  private boolean readOnly;

  public TenantAccessInfo() {
  }

  public TenantAccessInfo(Integer idTenant, String tenantName, boolean home, TenantAccessLevel accessLevel) {
    this.idTenant = idTenant;
    this.tenantName = tenantName;
    this.home = home;
    this.accessLevel = accessLevel;
    this.readOnly = accessLevel == TenantAccessLevel.READ;
  }

  public Integer getIdTenant() {
    return idTenant;
  }

  public void setIdTenant(Integer idTenant) {
    this.idTenant = idTenant;
  }

  public String getTenantName() {
    return tenantName;
  }

  public void setTenantName(String tenantName) {
    this.tenantName = tenantName;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public boolean isHome() {
    return home;
  }

  public void setHome(boolean home) {
    this.home = home;
  }

  public TenantAccessLevel getAccessLevel() {
    return accessLevel;
  }

  public void setAccessLevel(TenantAccessLevel accessLevel) {
    this.accessLevel = accessLevel;
    this.readOnly = accessLevel == TenantAccessLevel.READ;
  }

  public boolean isReadOnly() {
    return readOnly;
  }

  public void setReadOnly(boolean readOnly) {
    this.readOnly = readOnly;
  }
}
