package grafiosch.types;

import java.util.Arrays;

/**
 * Access level a user holds on a tenant via a {@code tenant_access} grant.
 *
 * <p>
 * Used for the many-to-many access model that lets a single user (for example a portfolio advisor) work in several
 * tenants. {@link #READ} grants view-only access (all write operations on the tenant's data are rejected), while
 * {@link #MANAGE} grants full read/write (CRUD) access. A user's own home tenant ({@code user.id_tenant}) always
 * implies {@code MANAGE} unless {@code user.home_tenant_read_only} is set.
 * </p>
 */
public enum TenantAccessLevel {
  READ((byte) 0), MANAGE((byte) 1);

  private final Byte value;

  private TenantAccessLevel(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static TenantAccessLevel getTenantAccessLevelByValue(byte value) {
    return Arrays.stream(TenantAccessLevel.values()).filter(e -> e.getValue().equals(value)).findFirst().orElse(null);
  }
}
