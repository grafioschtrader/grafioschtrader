/**
 * One tenant the current user may access, returned by GET /tenant/accessible. Includes the user's own home tenant
 * (home = true) and every tenant granted to them (home = false). Used to populate the client switcher.
 */
export interface TenantAccessInfo {
  idTenant: number;
  tenantName: string;
  /** Login e-mail of the user owning this tenant (the read-only client for a granted tenant). */
  email: string;
  /** True if this is the user's own home tenant rather than a granted tenant. */
  home: boolean;
  /** Access level on this tenant: 'READ' or 'MANAGE'. */
  accessLevel: string;
  /** True when the user has only read (view-only) access to this tenant. */
  readOnly: boolean;
}
