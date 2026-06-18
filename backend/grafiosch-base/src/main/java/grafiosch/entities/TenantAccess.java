package grafiosch.entities;

import java.io.Serializable;

import grafiosch.types.TenantAccessLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;

/**
 * Grants a user access to a tenant other than (or in addition to) their own home tenant, together with the level of
 * access. This realizes the many-to-many access model anticipated by {@code User.actualIdTenant}: a single user (for
 * example a portfolio advisor) can manage several client tenants, and a client can be limited to read-only access.
 *
 * <p>
 * A user's home tenant ({@code user.id_tenant}) is <strong>not</strong> represented here; it always implies
 * {@link TenantAccessLevel#MANAGE} unless {@code user.home_tenant_read_only} is set. Only ids are stored (no JPA
 * relationship objects), mirroring how {@code User.idTenant} is a scalar foreign key.
 * </p>
 */
@Schema(description = """
    Grants a user access to a tenant other than their home tenant, with an access level (READ or MANAGE). Used so an
    advisor can manage several client tenants. The home tenant is not listed here and always implies MANAGE unless the
    user is flagged read-only on their home tenant.""")
@Entity
@Table(name = TenantAccess.TABNAME, uniqueConstraints = @UniqueConstraint(columnNames = { "id_user", "id_tenant" }))
public class TenantAccess extends BaseID<Integer> implements Serializable {

  public static final String TABNAME = "tenant_access";

  private static final long serialVersionUID = 1L;

  @Schema(description = "Unique identifier of the tenant access grant")
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_tenant_access")
  private Integer idTenantAccess;

  @Schema(description = "The user that is granted access")
  @NotNull
  @Column(name = "id_user")
  private Integer idUser;

  @Schema(description = "The tenant the user is granted access to")
  @NotNull
  @Column(name = "id_tenant")
  private Integer idTenant;

  @Schema(description = "Access level: 0 = READ (view-only), 1 = MANAGE (full CRUD)")
  @Column(name = "access_level")
  private byte accessLevel;

  public TenantAccess() {
  }

  public TenantAccess(Integer idUser, Integer idTenant, TenantAccessLevel accessLevel) {
    this.idUser = idUser;
    this.idTenant = idTenant;
    setAccessLevel(accessLevel);
  }

  public Integer getIdTenantAccess() {
    return idTenantAccess;
  }

  public void setIdTenantAccess(Integer idTenantAccess) {
    this.idTenantAccess = idTenantAccess;
  }

  public Integer getIdUser() {
    return idUser;
  }

  public void setIdUser(Integer idUser) {
    this.idUser = idUser;
  }

  public Integer getIdTenant() {
    return idTenant;
  }

  public void setIdTenant(Integer idTenant) {
    this.idTenant = idTenant;
  }

  public TenantAccessLevel getAccessLevel() {
    return TenantAccessLevel.getTenantAccessLevelByValue(accessLevel);
  }

  public void setAccessLevel(TenantAccessLevel accessLevel) {
    this.accessLevel = accessLevel.getValue();
  }

  @Override
  public Integer getId() {
    return idTenantAccess;
  }
}
